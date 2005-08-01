/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;

/*
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
    private static final String ASCII = "ASCII";

    private int readInt() throws IOException{
	byte buf[] = new byte[4];
	in.read(buf);

	return ((0xFF&buf[0])<<24) + ((0xFF&buf[1])<<16) + ((0xFF&buf[2])<<8) + (0xFF&buf[3]);
    }
    private String readString(int length) throws IOException {
	byte buf[] = new byte[length];
	in.read(buf);
	return new String(buf, ASCII);
    }

    private String readName() throws IOException {
	return readString(readInt());
    }

    void init(InputStream in, OutputStream out, Socket sock) throws IOException {
	this.in = in;
	this.out = out;
	this.sock = sock;
    	ctx = (Context) Context.get(readName());
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
	}
    }
}
