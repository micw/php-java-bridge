/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.DynamicJavaBridgeClassLoader;

/**
 * The ContextRunner manages the "high speed" communication link.  It
 * pulls a context and executes it.  After execution the context is destroyed.
 */
class ContextRunner implements Runnable {
    		
    private IContextFactory ctx;
    private JavaBridge bridge;
    private InputStream in;
    private OutputStream out;
    private IContextServer.Channel channel;
    private Request r;
    private ContextServer contextServer;

    protected ContextRunner(ContextServer contextServer, IContextServer.Channel channel) throws FileNotFoundException {
	this.contextServer = contextServer;
	this.channel = channel;
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
	out = channel.getOuptutStream();
	in = channel.getInputStream();

	int c = in.read();
	if(c!=077) {
	    try {out.write(0); }catch(IOException e){}
	    throw new IOException("Protocol violation");
	}
	if(!contextServer.getNext()) {
	    Util.logFatal("Could not find a runner for the request I've received. This is either a bug in the software or an intruder is accessing the local communication channel. Please check the log file(s).");
	    throw new IOException("No runner available");
	}
	String name = readName();
    	ctx = (IContextFactory) ContextFactory.get(name, contextServer);
    	if(ctx == null) throw new IOException("No context available for: " + name + ".");
    	bridge = ctx.getBridge();
	// The first statement was executed with the default
	// classloader, now set the dynamic class loader into the
	// bridge:
	JavaBridgeClassLoader loader = bridge.getClassLoader();
	DynamicJavaBridgeClassLoader xloader = null;
	try {
	    xloader = 
		(DynamicJavaBridgeClassLoader) 
		Thread.currentThread().getContextClassLoader();
	} 
	catch (SecurityException e) {/*ignore*/}
	catch (ClassCastException e1) {/*ignore*/}
	loader.setClassLoader(xloader);
	
	r = bridge.request;
	r.reset();
    	bridge.in=in;
    	bridge.out=out;
    }
    public void run() {
	try {
	    init();
	    r.handleRequests();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	} finally {
	    if(ctx!=null) ctx.destroy();
	    channel.shutdown();
	    //JavaBridge.load--;
	}
    }
}
