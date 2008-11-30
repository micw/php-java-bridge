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

import php.java.bridge.AppThreadPool;
import php.java.bridge.Util;
import php.java.bridge.http.ContextFactory.ICredentials;

/**
 * A bridge pattern which either uses the PipeContextServer or the SocketContextServer, 
 * depending on the OS and/or the security restrictions. On windows, which cannot use named pipes,
 * a SocketContextServer is used. All other operating systems use a PipeContextServer unless the 
 * system property php.java.bridge.promiscuous is set to true or the system property
 * php.java.bridge.no_pipe_server is set to true.
 * <p>
 * A ContextServer instance represents the current web context. 
 * When the PipeContextServer is used, there can be more than one PipeContextServer instance per classloader, the ContextFactory.get() checks
 * if it is called with the same ContextServer and throws a SecurityException otherwise. So one cannot access contexts belonging to other web contexts.
 * </p><p>
 * The SocketContextServer uses only one server socket for all shared web contexts and cannot do any security checks.
 * </p>
 * @author jostb
 * @see php.java.bridge.http.PipeContextServer
 * @see php.java.bridge.http.SocketContextServer
 */
public final class ContextServer implements ContextFactory.ICredentials {
    // One PipeContextServer for each web context, carries this ContextServer as a security token.
    private PipeContextServer ctx;
    private String contextName;
    // There's only one  shared SocketContextServer instance, otherwise we have to allocate a new ServerSocket for each web context
    // No secutiry token
    private  static SocketContextServer sock = null; 
    // One pool for both, the Socket- and the PipeContextServer
    private static final AppThreadPool pool = new AppThreadPool("JavaBridgeContextRunner", Integer.parseInt(Util.THREAD_POOL_MAX_SIZE));
    
    private class PipeChannelName extends AbstractChannelName {
        public PipeChannelName(String name, IContextFactory ctx) {super(name,  ctx);}

        public boolean startChannel() {
            return ctx.start(this);
        }
        public String toString() {
            return "Pipe:"+getName();
        }
    }
    private class SocketChannelName extends AbstractChannelName {
        public SocketChannelName(String name,IContextFactory ctx) {super(name,  ctx);}
        
        public boolean startChannel() {
            return sock.start(this);
        }
        public String toString() {
            return "Socket:"+getName();
        }
    }
    /**
     * Create a new ContextServer using a thread pool.
     * @param contextName The the name of the web context to which this server belongs.
     */
    public ContextServer(String contextName) {
        this.contextName = contextName;
	/*
         * We need both because the client may not support named pipes.
         */
        ctx = new PipeContextServer(this, pool, contextName);
        /* socket context server will be created on demand */
    }
    /**
     * Destroy the pipe or socket context server.
     */
    public void destroy() {
        ctx.destroy();
        if(sock!=null) sock.destroy();
        ContextFactory.destroyAll();
	php.java.bridge.SessionFactory.destroyTimer();
	php.java.bridge.DynamicClassLoader.destroyObserver();
	pool.destroy();
	Util.destroy();
    }

    /**
     * Check if either the pipe of the socket context server is available. This function
     * may try start a SocketContextServer, if a PipeContextServer is not available. 
     * @param channelName The header value for X_JAVABRIDGE_CHANNEL, may be null.  
     * @return true if either the pipe or the socket context server is available.
     */
    public boolean isAvailable(String channelName) {
        if(channelName!=null && ctx.isAvailable()) return true;
        SocketContextServer sock=getSocketContextServer(this, pool);
        return sock!=null && sock.isAvailable();
    }

    private static synchronized SocketContextServer getSocketContextServer(ContextServer server, AppThreadPool pool) {
	if(sock!=null) return sock;
	return sock=new SocketContextServer(pool);
    }

    /**
     * Start a channel name.
     * @param channelName The ChannelName.
     * @throws IllegalStateException if there's no Pipe- or SocketContextServer available
     */
    public void start(AbstractChannelName channelName) {
	boolean started = channelName.start();
	if(!started) throw new IllegalStateException("Pipe- and SocketContextServer not available");
    }

    /**
     * Return the channelName which be passed to the client as X_JAVABRIDGE_REDIRECT
     * @param channelName The name of the channel, see X_JAVABRIDGE_CHANNEL
     * @param currentCtx The current ContextFactory, see X_JAVABRIDGE_CONTEXT
     * @return The channel name of the Pipe- or SocketContextServer.
     */
    public AbstractChannelName getFallbackChannelName(String channelName, IContextFactory currentCtx) {
        if(channelName!=null && ctx.isAvailable()) return new PipeChannelName(channelName,  currentCtx);
        SocketContextServer sock=getSocketContextServer(this, pool);
        return new SocketChannelName(sock.getChannelName(),  currentCtx);
    }
    
    /**
     * Return the credentials provided by the ContextServer. The SocketContextServer is insecure and cannot provide any, because there's 
     * only one SocketContextServer instance for all shared web contexts. The PipeContextServer
     * returns a token which will be used in ContextFactory.get() to check if the web context is allowed to access this contextFactory instance or not.
     * @param channelName The name of the channel, see X_JAVABRIDGE_CHANNEL
     * @return A security token.
     */
    public ICredentials getCredentials(String channelName) {
        if(channelName!=null && ctx.isAvailable()) return this; // PipeContextServer
        return ContextFactory.NO_CREDENTIALS; // SocketContextServer 
    }
    
    /**{@inheritDoc}*/  
    public String toString () {
	return "ContextServer: " + contextName;
    }
}
