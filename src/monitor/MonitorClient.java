package monitor;

/*
 * Class MonitorClient
 * Runnable that sets up a client which periodically sends screenshots to a server specified.
 */
import static comms.Comms.*;

import java.net.Socket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

public class MonitorClient implements Runnable {
	Socket connection;
	Robot robot;
	DataOutputStream out;
	DataInputStream in;
	Timer timer;
	
	static Rectangle windowRectangle = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	static BufferedImage getScreenshot(Robot r) {
		return r.createScreenCapture(windowRectangle);
	}
	
	class MonitorRoutine extends TimerTask {
		@Override
		public void run() {
			try {
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				ImageIO.write(getScreenshot(robot), "jpg", outStream);
				byte[] data = outStream.toByteArray();
				outStream.close();
				out.writeInt(data.length);
				out.write(data);
			}
			catch(IOException e) {
				timer.cancel();
				timer.purge();
				try {
					in.close();
					out.close();
					connection.close();
				}
				catch(IOException e1) {
				}
				//This will cause an IOException to be thrown in the run() method of the main thread also
				//which triggers the exit code
			}
		}
		
	}
	
	public MonitorClient(String ip, int port) throws IOException, AWTException {
		connection = new Socket(ip, port);
		robot = new Robot();
		out = new DataOutputStream(connection.getOutputStream());
		in = new DataInputStream(connection.getInputStream());
	}
	
	@Override
	public void run() {
		timer = new Timer();
		timer.schedule(this.new MonitorRoutine(), 100, 45);
		
		try {
			while(in.readByte() != MON_CMD_EXIT) {
				Thread.sleep(500);
			}
		} catch (IOException e) {
			//Leave empty since either way the code after is cleanup
		} catch (InterruptedException e) {
		}
		
		timer.cancel();
		timer.purge();
		try {
			in.close();
			out.close();
			connection.close();
		}
		catch(IOException e) {
		}
		
	}

}
