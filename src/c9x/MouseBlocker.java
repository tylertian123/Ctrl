package c9x;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ImageIcon;

import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;

public class MouseBlocker implements Runnable {
	JFrame frame;
	volatile boolean running = false;
	Point prev = null;
	
	public MouseBlocker() {
		frame = new JFrame();
		frame.setUndecorated(true);
		frame.setBackground(new Color(0, 0, 0, 0));
		frame.setType(JFrame.Type.UTILITY);
		frame.setAlwaysOnTop(true);
		frame.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), "transparent cursor"));
		ImageIcon img;
		img = new ImageIcon(getClass().getResource("/resources/crossedout40x40.png"));
		frame.add(new JLabel(img));
		frame.pack();
		
	}
	
	void trackMouse() {
		Point posNew;
		if(!(posNew = MouseInfo.getPointerInfo().getLocation()).equals(prev)) {
			frame.setLocation(posNew.x - 20, posNew.y - 20);
		}
		prev = posNew;
	}
	
	@Override
	public void run() {
		running = true;
		trackMouse();
		frame.setVisible(true);
		while(running) {
			try {
				Thread.sleep(50);
				trackMouse();
			}
			catch(InterruptedException e) {
				running = false;
				break;
			}
		}
 	}
	
	public boolean isRunning() {
		return running;
	}
	public void stop() {
		running = false;
		frame.dispose();
	}
}
