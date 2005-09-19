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
 * This class manages the ContextRunners.  It pulls a ContextRunner
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
    synchronized boolean getNext() {
	if(runnables==0) return false;
	runnables--;
	return true;
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
	    try {socket = this.socket.accept();} catch (IOException ex) {return true;} // socket closed
	    in=socket.getInputStream();
	    out=socket.getOutputStream();
	    ContextRunner runner = new ContextRunner(this, in, out, socket);
	    if(PhpJavaServlet.threadPool!=null) {
	        PhpJavaServlet.threadPool.start(runner);
	    } else {
	    	Thread t = new Thread(runner);
		t.setContextClassLoader(DynamicJavaBridgeClassLoader.newInstance());
	    	t.start();

	    }
	} catch (SecurityException t) {
	    shutdownSocket(in, out, socket);
	    Context.removeAll();
	    Util.printStackTrace(t);
	    return false;
	} catch (Throwable t) {
	    shutdownSocket(in, out, socket);
	    Util.printStackTrace(t);
	}
	return true;
    }
    public void run() {
	while(socket!=null) {
	    if(!accept()) destroy();
	}
	Util.logMessage("Socket runner stopped, the local channel is not available anymore.");
    }
    synchronized void schedule() {
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
