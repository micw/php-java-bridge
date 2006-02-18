/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import php.java.bridge.ISocketFactory;
import php.java.bridge.JavaBridge;
import php.java.bridge.ThreadPool;
import php.java.bridge.Util;

/**
 * This class manages the ContextRunners.  It pulls a ContextRunner
 * from the list of available runners and invokes it.
 * 
 * We currently check only if the number of runners requested is less
 * than or equal to the number of runners created by the servlet
 * requests.
 * 
 */
public class SocketContextServer extends PipeContextServer implements Runnable {
    private ISocketFactory socket = null;
    protected static class Channel extends PipeContextServer.Channel {
        protected Socket sock;
        
        public Channel(InputStream in, OutputStream out, Socket sock) {
            super(in, out);
            this.sock = sock;
        }
        public InputStream getInputStream() {
            return in;
        }
        
        public OutputStream getOuptutStream() {
            return out;
        }
        
        public Socket getSocket() {
            return sock;
        }
        protected static void shutdown(Socket sock) {
            if(sock!=null) try {sock.close();}catch (IOException e){}           
             }

        public void shutdown() {
            super.shutdown();
            shutdown(sock);
         }    

    }
    /**
     * Create a new ContextServer using the ThreadPool. 
     * @param threadPool Obtain runnables from this pool. If null, new threads will be created.
     */
    public SocketContextServer (ContextServer contextServer, ThreadPool threadPool) {
    	super(contextServer, threadPool);
        try {
	    socket = JavaBridge.bind("INET_LOCAL:0");
	    try {
	        SecurityManager sec = System.getSecurityManager();
	        if(sec!=null) sec.checkAccept("127.0.0.1", Integer.parseInt(socket.getSocketName()));
	    } catch (SecurityException sec) {
	        throw new Exception("Add the line: grant {permission java.net.SocketPermission \"*\", \"accept,resolve\";}; to your server.policy file or run this AS on an operating system which supports named pipes (e.g.: Unix, Linux, BSD, Mac OSX, ...).", sec);
	    } catch (Throwable t) {/*ignore*/};
            Thread t = new Thread(this, "JavaBridgeContextServer");
	    t.setDaemon(true);
	    t.start();
        } catch (Throwable t) {
	    Util.warn("Local communication channel not available. The PHP/Java bridge will be very slow.");
            Util.printStackTrace(t);
            if(socket!=null) try{socket.close();}catch(IOException e) {}
            socket=null;
        }
    }

    private boolean accept() {
	InputStream in=null;
	OutputStream out=null;
	Socket socket=null;
	
	try {
	    try {socket = this.socket.accept();} catch (IOException ex) {return false;} // socket closed
	    in=socket.getInputStream();
	    out=socket.getOutputStream();
	    ContextRunner runner = new ContextRunner(contextServer, new Channel(in, out, socket));
	    if(threadPool!=null) {
	        threadPool.start(runner);
	    } else {
	    	Thread t = new Thread(runner, "JavaBridgeContextRunner");
	    	t.start();

	    }
	} catch (SecurityException t) {
	    (new Channel(in, out, socket)).shutdown();
	    ContextFactory.removeAll();
	    Util.printStackTrace(t);
	    return false;
	} catch (Throwable t) {
	    (new Channel(in, out, socket)).shutdown();
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

     /**
     * Destroy the server
     *
     */
    public void destroy() {
        super.destroy();
	if(socket!=null) {
	    try {socket.close();} catch (IOException e) {Util.printStackTrace(e);}
	    socket = null;
	}
    }
    
    /**
     * Check if the ContextServer is ready, i.e. it has created a server socket.
     * @return true if there's a server socket listening, false otherwise.
     */
    public boolean isAvailable() {
    	return socket!=null;
    }

    /**
     * Returns the server port.
     * @return The server port.
     */
    public String getChannelName() {
        return socket.getSocketName();
    }
    
    public boolean start(String channelName) {
        return isAvailable();
    }
}
