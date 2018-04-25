package tests;
import c9x.MouseBlocker;
public class MouseBlockerTest {

	public static void main(String[] args) {
		MouseBlocker blocker = new MouseBlocker();
		new Thread(blocker, "mouseblocker").start();
		try {
			Thread.sleep(30000);
		}
		catch(InterruptedException e) {
		}
		blocker.stop();
	}

}
