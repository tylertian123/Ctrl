package c9x;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;

public class Flicker implements Runnable {
	JFrame frame;
	volatile boolean running = false;
	public Flicker() {
		frame = new JFrame();
		JPanel panel = new JPanel();
		frame.setLocation(0, 0);
		frame.setUndecorated(true);
		frame.setType(JFrame.Type.UTILITY);
		frame.setAlwaysOnTop(true);
		panel.setPreferredSize(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
		panel.setOpaque(true);
		panel.setBackground(Color.BLACK);
		frame.add(panel);
		frame.pack();
	}
	public void stop() {
		running = false;
	}
	public boolean isRunning() {
		return running;
	}
	@Override
	public void run() {
		running = true;
		while(running) {
			try {
				Thread.sleep((long) (Math.random() * 10000));
			} catch (InterruptedException e1) {
				running = false;
			}
			
			if(!running) {
				break;
			}
			frame.setVisible(true);
			try {
				Thread.sleep(100);
			}
			catch(InterruptedException e) {
			}
			frame.setVisible(false);
		}
	}
}
