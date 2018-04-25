package monitor;

/*
 * Class MonitorServer
 * Runnable that sets up a server that periodically receives screenshots from the client.
 */
import static comms.Comms.*;

import java.net.Socket;
import java.net.ServerSocket;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ImageIcon;

public class MonitorServer implements Runnable {
	JFrame frame;
	JLabel label;
	ServerSocket server;
	Socket client = null;
	DataOutputStream out;
	DataInputStream in;
	volatile boolean running = true;
	
	class MonitorServerWindowListener extends WindowAdapter {
		@Override
		public void windowClosing(WindowEvent e) {
			running = false;
			frame.dispose();
			try {
				//Should cause an IOException in the main code which shuts down the window
				in.close();
			} catch (IOException e1) {
			}
		}
	}
	
	public MonitorServer(int port) throws IOException {
		this(port, "CtrlServer: Monitor");
	}
	public MonitorServer(int port, String name) throws IOException {
		server = new ServerSocket(port);
		frame = new JFrame();
		frame.add(label = new JLabel());
		frame.setTitle(name);
		frame.setResizable(false);
		frame.addWindowListener(this.new MonitorServerWindowListener());
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}
	public void stop() {
		running = false;
	}
	public void forceStop() throws IOException {
		running = false;
		server.close();
	}
	
	@Override
	public void run() {
		try {
			client = server.accept();
			out = new DataOutputStream(client.getOutputStream());
			in = new DataInputStream(client.getInputStream());
		}
		catch(IOException e) {
			//Probably caused by forced closing of server socket, so exit
			System.err.println("The Monitor service experienced an error when trying to establish a connection.");
			return;
		}
		while(running) {
			try {
				int size = in.readInt();
				byte[] data = new byte[size];
				in.readFully(data);
				ByteArrayInputStream inStream = new ByteArrayInputStream(data);
				BufferedImage imgRaw = ImageIO.read(inStream);
				inStream.close();
				Image img = imgRaw.getScaledInstance(imgRaw.getWidth() * 2/3, imgRaw.getHeight() * 2/3, Image.SCALE_SMOOTH);
				label.setIcon(new ImageIcon(img));
				if(frame.isVisible()) {
					frame.repaint();
				}
				else {
					frame.pack();
					frame.setVisible(true);
				}
			} catch (IOException e) {
				//If any kind of error occurs, do cleanup and exit
				System.err.println("The Monitor service has been shut down unexpectedly. This may be due to the shutdown of the client side application,  or network problems.");
				break;
			}
		}
		
		try {
			out.writeByte(MON_CMD_EXIT);
			out.close();
			in.close();
			client.close();
			server.close();
		}
		catch(IOException e) {
		}
		frame.dispose();
	}

}
