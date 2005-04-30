/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;

public class Listener implements Runnable {
	
	public static final String GROUP_ADDR = "239.255.6.10"; // Multicast-Addr
	public static final int GROUP_PORT = 9167;
	
	MulticastSocket socket;
	static final byte feature='J'; //[M]mono or [J]ava FIXME: Use autoconf
	private byte[] addr;
	private int port;
	
	/**
	 * @param socket
	 */
	public Listener(ServerSocket socket) {
		addr = socket.getInetAddress().getAddress();
		port = socket.getLocalPort();
	}
	public void listen() throws IOException {
		socket = new MulticastSocket(GROUP_PORT);
		InetAddress group = InetAddress.getByName(GROUP_ADDR); 
		socket.joinGroup(group);
		Thread t = new Thread(this);
		t.start();
	}
	
	private static void writeInt(ByteArrayOutputStream out, int value) {
		out.write(0xff&(value>>24));
		out.write(0xff&(value>>16));
		out.write(0xff&(value>>8));
		out.write(0xff&value);
	    }

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	DatagramPacket createResponse(int load, DatagramPacket p) throws IOException {
		byte[] b = new byte[3];
		byte[] data = p.getData();
		b[0]='r';
		b[1]=feature;
		b[2]=(byte) (load>255?255:(load&255));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(b);
		out.write(data, 3, 4); // send back timestamp

		writeInt(out, port);

		byte address[] = new byte[] {0, 0, 0, 0, 0, 0};
		System.arraycopy(addr, 0, address, 0, addr.length);
		out.write(addr.length==4?0:1);
		out.write(address);
		
		b=out.toByteArray();

		while(load-->0) try {Thread.sleep(8);} catch (InterruptedException ex) {}
		return new DatagramPacket(b, b.length, p.getAddress(), p.getPort());
	}
	public void run() {
		
		byte[] buf=new byte[18];			
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while(true) {
			try {
				socket.receive(packet);
				if(packet.getLength()!=buf.length) continue;
				byte[] data = packet.getData();
				if(data[0]!='R') continue;
				if(data[1]!=0 && data[1]!=feature) continue;
				int load = JavaBridge.getLoad();
				if(data[2]!=0 && load>data[2]) continue;
				socket.send(createResponse(load, packet));
			} catch (IOException e) {
				Util.printStackTrace(e);
			}
		}
	}
	

}
