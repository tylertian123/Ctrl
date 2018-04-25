package c9x;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;

public class BSODPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7351895869561607297L;
	
	Color BSOD_BACKGROUND;
	BufferedImage bsodImgUnscaled;
	Image bsodImage;
	boolean horizScaled;
	double horizRatio, vertRatio;
	static Toolkit tk = Toolkit.getDefaultToolkit();
	public BSODPanel() {
		super();
	}
	public BSODPanel(String pic, final Color background) throws IOException {
		super();
		BSOD_BACKGROUND = background;
		InputStream imgStream = getClass().getResourceAsStream(pic);
		bsodImgUnscaled = ImageIO.read(imgStream);
		Dimension d = tk.getScreenSize();
		horizRatio = d.getWidth() / bsodImgUnscaled.getWidth();
		vertRatio = d.getHeight() / bsodImgUnscaled.getHeight();
		if(horizRatio < vertRatio) {
			horizScaled = true;
			bsodImage = bsodImgUnscaled.getScaledInstance(
					(int) (Math.ceil(bsodImgUnscaled.getWidth() * horizRatio)), 
					(int) (Math.ceil(bsodImgUnscaled.getHeight() * horizRatio)), 
					Image.SCALE_SMOOTH);
		}
		else {
			horizScaled = false;
			bsodImage = bsodImgUnscaled.getScaledInstance(
					(int) (Math.ceil(bsodImgUnscaled.getWidth() * vertRatio)), 
					(int) (Math.ceil(bsodImgUnscaled.getHeight() * vertRatio)), 
					Image.SCALE_SMOOTH);
		}
	}
	public void changeImage(String pic, final Color background) throws IOException {
		BSOD_BACKGROUND = background;
		InputStream imgStream = getClass().getResourceAsStream(pic);
		bsodImgUnscaled = ImageIO.read(imgStream);
		Dimension d = tk.getScreenSize();
		horizRatio = d.getWidth() / bsodImgUnscaled.getWidth();
		vertRatio = d.getHeight() / bsodImgUnscaled.getHeight();
		if(horizRatio < vertRatio) {
			horizScaled = true;
			bsodImage = bsodImgUnscaled.getScaledInstance(
					(int) (Math.ceil(bsodImgUnscaled.getWidth() * horizRatio)), 
					(int) (Math.ceil(bsodImgUnscaled.getHeight() * horizRatio)), 
					Image.SCALE_SMOOTH);
		}
		else {
			horizScaled = false;
			bsodImage = bsodImgUnscaled.getScaledInstance(
					(int) (Math.ceil(bsodImgUnscaled.getWidth() * vertRatio)), 
					(int) (Math.ceil(bsodImgUnscaled.getHeight() * vertRatio)), 
					Image.SCALE_SMOOTH);
		}
	}
	@Override
	public void paintComponent(Graphics g) {
		Graphics2D graphics = (Graphics2D) g;
		graphics.setColor(BSOD_BACKGROUND);
		graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
		if(horizScaled) {
			int height = (int) (Math.ceil(bsodImgUnscaled.getHeight() * vertRatio));
			int offset = (tk.getScreenSize().height - height) / 2;
			graphics.drawImage(bsodImage, 0, offset, BSOD_BACKGROUND, this);
		}
		else {
			int width = (int) (Math.ceil(bsodImgUnscaled.getWidth() * vertRatio));
			int offset = (tk.getScreenSize().width - width) / 2;
			graphics.drawImage(bsodImage, offset, 0, BSOD_BACKGROUND, this);
		}
	}
}
