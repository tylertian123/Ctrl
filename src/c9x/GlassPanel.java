package c9x;

/*
 * Class GlassPanel
 * A specialized JFrame that is invisible, sits on top of everything and is exactly as big as the screen.
 * Blocks all mouse inputs and periodically requests focus to block keyboard inputs as well.
 */
import javax.swing.JFrame;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.Timer;
import java.util.TimerTask;

public class GlassPanel extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1027784087580745352L;
	
	Timer focusTimer = null;
	boolean enabled = false;
	class FocusTimerTask extends TimerTask {
		@Override
		public void run() {
			GlassPanel.this.toFront();
			GlassPanel.this.requestFocus();
		}
	}
	public GlassPanel() {
		super();
		this.setUndecorated(true);
		this.setLocation(0, 0);
		this.setSize(Toolkit.getDefaultToolkit().getScreenSize());
		this.setAlwaysOnTop(true);
		this.setType(JFrame.Type.UTILITY);
		this.setBackground(new Color(0, 0, 0, 1));
	}
	public boolean glassPanelIsEnabled() {
		return enabled;
	}
	public void enableGlassPanel() {
		if(enabled)
			return;
		enabled = true;
		focusTimer = new Timer();
		focusTimer.schedule(this.new FocusTimerTask(), 1000, 1000);
		this.setVisible(true);
	}
	public void disableGlassPanel() {
		if(!enabled)
			return;
		enabled = false;
		focusTimer.cancel();
		focusTimer.purge();
		this.setVisible(false);
	}
	@Override
	public void dispose() {
		if(enabled) {
			this.disableGlassPanel();
		}
		super.dispose();
	}
}
