package c9x;

import javax.swing.JFrame;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.awt.Color;
import java.awt.Point;

public class BSODFrame extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7687108485840718056L;
	boolean enabled = false;
	public void enableBSOD() {
		if(!enabled)
			this.setVisible(true);
		enabled = true;
	}
	public void disableBSOD() {
		if(enabled)
			this.setVisible(false);
		enabled = false;
	}
	public boolean getEnabled() {
		return enabled;
	}
	@Override
	public void dispose() {
		if(enabled)
			this.disableBSOD();
		super.dispose();
	}
	
	BSODPanel bsodPanel;
	public BSODFrame() {
		super();
		bsodPanel = new BSODPanel();
		Toolkit tk = Toolkit.getDefaultToolkit();
		this.setUndecorated(true);
		this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		this.setSize(tk.getScreenSize());
		this.setCursor(tk.createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), "transparent cursor"));
		this.setAlwaysOnTop(true);
		this.setType(JFrame.Type.UTILITY);
		this.add(bsodPanel);
	}
	public BSODFrame(String os) throws IOException {
		super();
		switch(os) {
		case "win10":
		case "win8":
			bsodPanel = new BSODPanel("/resources/bsod_win10_big.png", new Color(0, 120, 215));
			break;
		case "win7":
		case "winvista":
			bsodPanel = new BSODPanel("/resources/bsod_win7.png", new Color(2, 14, 134));
			break;
		case "macosx":
			bsodPanel = new BSODPanel("/resources/kernelpanic_mac.jpg", new Color(34, 34, 34));
			break;
		case "linux":
			bsodPanel = new BSODPanel("/resources/kernelpanic_linux.png", new Color(0, 0, 0));
			break;
		default:
			throw new IllegalArgumentException("OS not supported");
		}
		
		Toolkit tk = Toolkit.getDefaultToolkit();
		this.setUndecorated(true);
		this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		this.setSize(tk.getScreenSize());
		this.setCursor(tk.createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), "transparent cursor"));
		this.setAlwaysOnTop(true);
		this.setType(JFrame.Type.UTILITY);
		this.add(bsodPanel);
	}
	public void setBSODOS(String osName) throws IOException {
		switch(osName) {
		case "win10":
		case "win8":
			bsodPanel.changeImage("/resources/bsod_win10_big.png", new Color(0, 120, 215));
			break;
		case "win7":
		case "winvista":
			bsodPanel.changeImage("/resources/bsod_win7.png", new Color(2, 14, 134));
			break;
		case "macosx":
			bsodPanel.changeImage("/resources/kernelpanic_mac.jpg", new Color(34, 34, 34));
			break;
		case "linux":
			bsodPanel.changeImage("/resources/kernelpanic_linux.png", new Color(0, 0, 0));
			break;
		default:
			throw new IllegalArgumentException("OS not supported");
		}
	}
}
