package tests;

import java.awt.EventQueue;

import c9x.GlassPanel;

public class GlassPanelTest {

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				GlassPanel glassPanel = new GlassPanel();
				glassPanel.enableGlassPanel();
				try {
					Thread.sleep(5000);
				}
				catch(InterruptedException e) {
					e.printStackTrace();
				}
				glassPanel.dispose();
			}
		});
	}

}
