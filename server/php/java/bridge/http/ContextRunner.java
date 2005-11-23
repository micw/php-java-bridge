/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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
    		
    private ContextFactory ctx;
    private JavaBridge bridge;
    private InputStream in;
    private OutputStream out;
    private Socket sock;
    private Request r;
    private ContextServer runner;

    protected ContextRunner(ContextServer runner, InputStream in, OutputStream out, Socket sock) {
	this.runner = runner;
	this.in = in;
	this.out = out;
	this.sock = sock;
    }
    private int readLength() throws IOException{
	byte buf[] = new byte[1];
	in.read(buf);

	return (0xFF&buf[0]);
    }
    private String readString(int length) throws IOException {
	byte buf[] = new byte[length];
	in.read(buf);
	return new String(buf, Util.ASCII);
    }

    private String readName() throws IOException {
	return readString(readLength());
    }

    private void init() throws IOException {
	int c = in.read();
	if(c!=077) {
	    try {out.write(0); }catch(IOException e){}
	    throw new IOException("Protocol violation");
	}
	if(!runner.getNext()) {
	    Util.logFatal("Could not find a runner for the request I've received. This is either a bug in the software or an intruder is accessing the local communication channel. Please check the log file(s).");
	    throw new IOException("No runner available");
	}
	String name = readName();
    	ctx = (ContextFactory) ContextFactory.get(name);
    	if(ctx == null) throw new IOException("No context available for: " + name + ".");
    	bridge = ctx.getBridge();
    	bridge.in=in;
    	bridge.out=out;
	r = bridge.request = new Request(bridge);
    }
    public void run() {
	try {
	    init();
	    r.handleRequests();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	} finally {
	    if(ctx!=null) ctx.remove();
	    ContextServer.shutdownSocket(in, out, sock);
	    JavaBridge.load--;
	}
    }
}
