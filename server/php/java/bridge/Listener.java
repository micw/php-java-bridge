/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;

public class Listener implements Runnable {
	
        public static final int LOAD_PENALTY = 8; // ms to wait until we
						// respond to the next
						// request

        public static final int MAX_LOAD = 12; // load in the range [0..MAX_LOAD[


	public static final String GROUP_ADDR = "239.255.6.10"; // Multicast-Addr
	public static final int GROUP_PORT = 9168;
	
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
	public void listen()  {
		try {
			socket = new MulticastSocket(GROUP_PORT);
		} catch (IOException e) {
			Util.logMessage("Could not start multicast listener. Load balancing not available: " + e);
			return;
		}
		try {
			InetAddress group = InetAddress.getByName(GROUP_ADDR); 
			socket.joinGroup(group);
		} catch (IOException ex) {
			socket.close();
			Util.logMessage("Could not start multicast listener. Load balancing not available: " + ex);
			return;
		}
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
	DatagramPacket createResponse(short load, DatagramPacket p) throws IOException {
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
				Util.logDebug("packet:::" + data[0] + " " + data[2] + " load:" + JavaBridge.getLoad());
				if(data[0]!='R') continue;
				if(data[1]!=0 && data[1]!=feature) continue;
				short load = JavaBridge.getLoad();
				if(load>=MAX_LOAD) continue;
				// FIXME: field temporarily used to transfer client pid
				//if(data[2]!=0 && load>data[2]) continue;
				socket.send(createResponse(load, packet));

				// FIXME: doesn't work at all
// 				try {Thread.sleep(LOAD_PENALTY*load);} catch (InterruptedException ex) {}
// 				// do not respond if load gets too high
// 				synchronized(JavaBridge.loadLock) {
// 				    if(JavaBridge.getLoad()>=MAX_LOAD) {
// 					try {
// 					    Util.logDebug("waiting on lock:" + this +  " " +load);
// 					    JavaBridge.loadLock.wait();
// 					    Util.logDebug("done waiting on lock:" + this +  " " +load);
// 					} catch (java.lang.InterruptedException bleh) {}
// 				    }
// 				}
			} catch (IOException e) {
			    Util.printStackTrace(e);
			}
		}
	}
}
