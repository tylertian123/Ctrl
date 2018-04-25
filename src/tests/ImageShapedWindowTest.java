package tests;

import javax.swing.*;
import java.awt.*;

public class ImageShapedWindowTest {
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable () {
			@Override
			public void run() {
				JFrame frame = new JFrame();
				frame.setUndecorated(true);
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setBackground(new Color(0, 0, 0, 0));
				ImageIcon img = new ImageIcon(getClass().getResource("/resources/crossedout.png"));
				frame.add(new JLabel(img));
				frame.pack();
				frame.setVisible(true);
			}
		});
	}
}
