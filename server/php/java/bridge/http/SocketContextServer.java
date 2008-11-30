/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import php.java.bridge.AppThreadPool;
import php.java.bridge.ISocketFactory;
import php.java.bridge.JavaBridge;
import php.java.bridge.Util;

/**
 * This class manages the fallback physical connection for the
 * operating system which doesn't support named pipes, "Windows", or when the 
 * System property php.java.bridge.promiscuous is set to true.
 * <p>
 * When isAvailable() returns true, a server socket bound to the local
 * interface (127.0.0.1) has been created on a port in the range
 * [9267,...,[9367 and will be used for further communication, see
 * response header X_JAVABRIDGE_REDIRECT.  If this communication
 * channel is not available either, the PHP clients must continue to
 * send all statements via PUT requests. 
 * </p>
 *  <p> It is possible to switch
 * off this server by setting the VM property
 * php.java.bridge.no_socket_server to true, e.g.:
 * -Dphp.java.bridge.no_socket_server=true.  </p>
 * @see php.java.bridge.http.PipeContextServer
 * @see php.java.bridge.http.ContextServer
 */
public final class SocketContextServer extends PipeContextServer implements Runnable {
    private ISocketFactory serverSocket = null;
    protected List sockets = Collections.synchronizedList(new ArrayList());
    
    protected class Channel extends PipeContextServer.Channel {
        protected Socket sock;
        
        public Channel(String name, InputStream in, OutputStream out, Socket sock) {
            super(name, in, out);
	    sockets.add(this.sock = sock);
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
        protected void shutdown(Socket sock) {
            if(sockets.remove(sock)) try {sock.close();}catch (IOException e){}
        }

        public void shutdown() {
            super.shutdown();
            shutdown(sock);
         }    
    }
    private static final String BIND_PORT=Util.JAVABRIDGE_PROMISCUOUS?"INET:0":"INET_LOCAL:0";
    /**
     * Create a new ContextServer using the ThreadPool. 
     * @param threadPool Obtain runnables from this pool. If null, new threads will be created.
     */
    public SocketContextServer (AppThreadPool threadPool) {
    	super(ContextFactory.NO_CREDENTIALS, threadPool, ContextFactory.EMPTY_CONTEXT_NAME);
        try {
	    serverSocket = JavaBridge.bind(BIND_PORT);
	    try {
	        SecurityManager sec = System.getSecurityManager();
	        if(sec!=null) sec.checkAccept("127.0.0.1", Integer.parseInt(serverSocket.getSocketName()));
	    } catch (SecurityException sec) {
	        throw new Exception("Add the line: grant {permission java.net.SocketPermission \"*\", \"accept,resolve\";}; to your server.policy file or run this AS on an operating system which supports named pipes (e.g.: Unix, Linux, BSD, Mac OSX, ...).", sec);
	    } catch (Throwable t) {/*ignore*/};
            Thread t = new Util.Thread(this, "JavaBridgeSocketContextServer("+serverSocket.getSocketName()+")");
	    t.start();
        } catch (Throwable t) {
	    Util.warn("Local communication channel not available. The PHP/Java bridge will be very slow.");
            Util.printStackTrace(t);
            if(serverSocket!=null) try{serverSocket.close();}catch(IOException e) {}
            serverSocket=null;
        }
    }

    private boolean accept() {
	InputStream in=null;
	OutputStream out=null;
	Socket socket=null;
	Channel channel = null;
	try {
	    try {socket = this.serverSocket.accept();} catch (IOException ex) {return false;} // socket closed
	    in=socket.getInputStream();
	    out=socket.getOutputStream();
	    ContextRunner runner = new ContextRunner(contextServer, channel = new Channel(getChannelName(), in, out, socket));
	    if(threadPool!=null) {
	        threadPool.start(runner);
	    } else {
	    	Thread t = new Util.Thread(runner, "JavaBridgeContextRunner(" + contextName+")");
	    	t.start();
	    }
	} catch (SecurityException t) {
	    if(channel!=null) channel.shutdown(socket);
	    ContextFactory.destroyAll();
	    Util.printStackTrace(t);
	    return false;
	} catch (Throwable t) {
	    if(channel!=null) channel.shutdown(socket);
	    Util.printStackTrace(t);
	}
	return true;
    }
    /**{@inheritDoc}*/
    public void run() {
	while(serverSocket!=null) {
	    if(!accept()) destroy();
	}
	Util.logDebug("SocketContextServer stopped, the local channel is not available anymore.");
    }

    private void closeAllSockets () {
	    synchronized (sockets) {
		    for (Iterator ii = sockets.iterator(); ii.hasNext(); ) {
			    Socket sock = (Socket)ii.next();
			    ii.remove();
			    try {sock.close();}catch (IOException e){}
		    }
	    }
    }
     /**
     * Destroy the server
     *
     */
    public void destroy() {
        super.destroy();

        closeAllSockets();

	if(serverSocket!=null) {
	    try {serverSocket.close();} catch (IOException e) {Util.printStackTrace(e);}
	    serverSocket = null;
	}
    }
    
    private static final boolean socketServer = checkTestTunnel("php.java.bridge.no_socket_server");
    /**
     * Check if the ContextServer is ready, i.e. it has created a server socket.
     * @return true if there's a server socket listening, false otherwise.
     */
    public boolean isAvailable() {
    	return socketServer && serverSocket!=null;
    }

    /**
     * Returns the server port.
     * @return The server port.
     */
    public String getChannelName() {
        return serverSocket.getSocketName();
    }
    /**{@inheritDoc}*/
    public boolean start(AbstractChannelName channelName) {
	return isAvailable();
    }
}
