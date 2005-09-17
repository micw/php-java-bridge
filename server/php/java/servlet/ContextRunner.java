/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;

/**
 * The ContextRunner manages the "high speed" communication link.  It
 * pulls a context and executes it.  After execution the context is destroyed.
 */
class ContextRunner implements Runnable {
    		
    private Context ctx;
    private JavaBridge bridge;
    private InputStream in;
    private OutputStream out;
    private Socket sock;
    private Request r;

    private int readLength() throws IOException{
	byte buf[] = new byte[1];
	in.read(buf);

	return (0xFF&buf[0]);
    }
    private String readString(int length) throws IOException {
	byte buf[] = new byte[length];
	in.read(buf);
	return new String(buf, PhpJavaServlet.ASCII);
    }

    private String readName() throws IOException {
	return readString(readLength());
    }

    void init(InputStream in, OutputStream out, Socket sock) throws IOException {
	this.in = in;
	this.out = out;
	this.sock = sock;
	String name = readName();
    	ctx = (Context) Context.get(name);
    	if(ctx == null) throw new NullPointerException("No context available for: " + name + ".");
    	bridge = ctx.bridge;
    	bridge.in=in;
    	bridge.out=out;
	r = bridge.request = new Request(bridge);
    }
    public void run() {
	try {
	    r.handleRequests();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	} finally {
	    ctx.remove();
	    SocketRunner.shutdownSocket(in, out, sock);
	    JavaBridge.load--;
	}
    }
}
