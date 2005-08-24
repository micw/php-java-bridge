/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.ISocketFactory;
import php.java.bridge.JavaBridge;
import php.java.bridge.Util;

/*
 * This class manages the ContextRunner's.  It pulls a ContextRunner
 * from the list of available runners and invokes it.
 * 
 * We currently check only if the number of runners requested is less
 * then or equal to the number of runners created by the servlet
 * requests.
 * 
 */
class SocketRunner implements Runnable {
    ISocketFactory socket = null; 
    SocketRunner () {
        try {
	    socket = JavaBridge.bind("INET:0");
	    Thread t = new Thread(this, "JavaBridgeSocketRunner");
	    t.setDaemon(true);
	    t.start();
        } catch (Throwable t) {
	    Util.logMessage("Warning: Local communication channel not available. The PHP/Java bridge will be slow.");
            Util.printStackTrace(t);
            if(socket!=null) try{socket.close();}catch(IOException e) {}
            socket=null;
        }
    }

    private int runnables = 0;
    private synchronized ContextRunner getNext() {
	if(runnables==0) return null;
	runnables--;
	return new ContextRunner();
    }
    static void shutdownSocket(InputStream in, OutputStream out, Socket sock) {
	if(in!=null) try {in.close();}catch (IOException e){}
	if(out!=null) try {out.close();}catch (IOException e){}
	if(sock!=null) try {sock.close();}catch (IOException e2){}
    }    
    private boolean accept() {
	InputStream in=null;
	OutputStream out=null;
	Socket socket=null;
	
	try {
	    try {socket = this.socket.accept();} catch (IOException ex) {return false;} // socket closed
	    in = socket.getInputStream();
	    out = socket.getOutputStream();
	    int c = in.read();
	    if(c!=077) {
	    	try {out.write(0); }catch(IOException e){Util.printStackTrace(e);}
	    	shutdownSocket(in, out, socket);
	    	return true; // protocol violation or PING or EOF
	    }
	    ContextRunner runner = getNext();
	    if(runner==null) {
	    	shutdownSocket(in, out, socket);
	    	Context.removeAll();
	    	Util.logFatal("Could not find a runner for the request I've received. This is either a bug in the software or an intruder is accessing the local communication channel. Please check the log file(s).");
	    	return false; // someone is accessing our local port!?
	    }
	    runner.init(in, out, socket);
	    if(PhpJavaServlet.threadPool!=null) {
	        PhpJavaServlet.threadPool.start(runner);
	    } else {
	    	Thread t = new Thread(runner);
		t.setContextClassLoader(DynamicJavaBridgeClassLoader.newInstance());
	    	t.start();

	    }
	} catch (IOException e) {
            shutdownSocket(in, out, socket);
            Util.printStackTrace(e);
	}
	return true;
    }
    public void run() {
	while(socket!=null) {
	    if(!accept()) destroy();
	}
	Util.logMessage("Socket runner stopped, the local channel is not available anymore.");
    }
    public synchronized void schedule() {
	runnables++;
    }

    public void destroy() {
	if(socket!=null) {
	    try {socket.close();} catch (IOException e) {Util.printStackTrace(e);}
	    socket = null;
	}
    }
    
    public boolean isAvailable() {
    	return socket!=null;
    }
}
