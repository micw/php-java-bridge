import java.io.*;
import java.net.*;
import java.util.*;
/**
 * This program checks if your operating system contains John Nagle's
 * TCP/IP "ack delay" bug fix.  If not, the second test will run up to
 * 10 times slower
 * 
 * Type  java TestServ  to run this test.
 */
public class TestServ implements Runnable {
    static final int SIZE=8192;
    static final int PORT=9767;

    private String getID(byte[] b, int n) {
	String str = (new String(b, 0, n));
	//System.out.println(str);
	int idx = str.lastIndexOf("=", 0); idx+=2;
	int idx2= str.indexOf("\"", idx);
	return str.substring(idx, idx2);
    }    

    public void run() {
	try {
	    doRun();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    public void doRun() throws Exception {
	int n;
	ServerSocket ss = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"));
	while(true) {
	    byte[] b = new byte[SIZE];
	    Socket s = ss.accept();

	    InputStream in = s.getInputStream();
	    in.read(b, 0, 1); //options
	    n = in.read(b, 0, 51);
	    
	    OutputStream out = s.getOutputStream();
	    byte[] x = ("<N i=\""+getID(b, n)+"\"/>").getBytes();
	    out.write(x, 0, x.length);

	    s.close();
	}
    }

    private static void runTest1() throws Exception {
	for(int i=0; i<200; i++) {
	    Socket s = new Socket("127.0.0.1", PORT);
	    InputStream in = s.getInputStream();
	    OutputStream out = s.getOutputStream();
	    out.write(("@<I v=\"0\" m=\"lastException\" p=\"P\" i=\"136070284\"></I>").getBytes());
	    byte[] b = new byte[1024];
	    in.read(b);
	    s.close();
	}
    }

    // same as above, buf send two packets
    private static void runTest2() throws Exception {
	for(int i=0; i<200; i++) {
	    Socket s = new Socket("127.0.0.1", PORT);
	    InputStream in = s.getInputStream();
	    OutputStream out = s.getOutputStream();
	    out.write('@');
	    out.write(("<I v=\"0\" m=\"lastException\" p=\"P\" i=\"136070284\"></I>").getBytes());
	    byte[] b = new byte[1024];
	    in.read(b);
	    s.close();
	}
    }	
    public static void main(String _s[]) throws Exception {
	Thread t = new Thread(new TestServ());
	t.start();
	Thread.sleep(100);

	long T1 = System.currentTimeMillis();
	runTest1();
	long T2 = System.currentTimeMillis();
	runTest2();
	long T3 = System.currentTimeMillis();
	    
	System.out.println("The following test demonstrates a bug in the BSD kernel:");
	System.out.println("Both tests transfer the same amount of data.");
	System.out.println("But the second test splits the packet after the first byte.");
	System.out.println("Test1: "+(T2-T1));
	System.out.println("Test2: "+(T3-T2));
	short c = (short)((T3-T2)/(T2-T1));
	System.out.println("Test2/Test1: "+c);
	if(c>1) {
	    System.out.println("Test failed");
	    System.exit(1);
	}
	System.out.println("Test okay");
	System.exit(0);
    }
}

