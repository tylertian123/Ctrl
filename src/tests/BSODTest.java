import java.io.IOException;

import c9x.BSODFrame;

public class BSODTest {

	public static void main(String[] args) {
		final BSODFrame bsodFrame;
		try {
			bsodFrame = new BSODFrame("linux");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(0);
			return;
		}
		bsodFrame.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				bsodFrame.dispose();
				System.exit(0);
			}
		});
		bsodFrame.enableBSOD();
	}

}
