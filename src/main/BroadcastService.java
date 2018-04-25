package main;

/*
 * Class BroadcastService
 * Periodically broadcasts the DatagramPacket specified over the local network
 * with the DatagramSocket specified. 
 * Usage: new Thread(new BroadcastService(socket, packet), "name").start();
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class BroadcastService implements Runnable {
	volatile boolean running = true;
	private static DatagramSocket dSocket;
	private static DatagramPacket dPacket;
	
	public BroadcastService(DatagramSocket socket, DatagramPacket packet) {
		dSocket = socket;
		dPacket = packet;
	}
	
	public void stop() {
		running = false;
	}
	@Override
	public void run() {
		while(running) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				System.err.println("BroadcastService sleep interrupted:");
				e1.printStackTrace();
			}
			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				while(interfaces.hasMoreElements() && running) {
					NetworkInterface intf = (NetworkInterface) interfaces.nextElement();
					if(intf.isLoopback() || !intf.isUp()) {
						continue;
					}
					for(InterfaceAddress addr : intf.getInterfaceAddresses()) {
						if(!running) {
							break;
						}
						InetAddress broadcast = addr.getBroadcast();
						if(broadcast == null) {
							continue;
						}
						
						dPacket.setAddress(broadcast);
						dSocket.send(dPacket);
						//System.out.println("Broadcast address discovered: " + broadcast.getHostName());
					}
				}
			} 
			catch (IOException e) {
				System.out.println("Failed to send data packet:");
				e.printStackTrace();
			}
		}
	}
}
