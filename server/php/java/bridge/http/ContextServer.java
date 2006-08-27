/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

/*
 * Copyright (C) 2006 Jost Boekemeier
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

import php.java.bridge.ThreadPool;

/**
 * A bridge pattern which either uses the PipeContextServer or the SocketContextServer, 
 * depending on the OS and/or the security restrictions.
 * <p>
 * A ContextServer instance represents the current web context. 
 * There can be more than one ContextServer instance per classloader, but the ContextFactory.get() checks
 * if it is called with the same ContextServer and throws a SecurityException otherwise. 
 * So one cannot access contexts belonging to other ContextServers.
 * </p>
 * @author jostb
 * @see php.java.bridge.http.PipeContextServer
 * @see php.java.bridge.http.SocketContextServer
 */
public final class ContextServer {
    private PipeContextServer ctx;
    private SocketContextServer sock = null;
    private ThreadPool pool;
    
    private class PipeChannelName extends IContextServer.ChannelName {
        public PipeChannelName(String name, String kontext, IContextFactory ctx) {super(name, kontext, ctx);}

        public boolean startChannel() {
            return ctx.start(this);
        }
        public String toString() {
            return "Pipe:"+getDefaultName();
        }
    }
    private class SocketChannelName extends IContextServer.ChannelName {
        public SocketChannelName(String name, String kontext, IContextFactory ctx) {super(name, kontext, ctx);}
        
        public boolean startChannel() {
            return sock.start(this);
        }
        public String toString() {
            return "Socket:"+getDefaultName();
        }
    }
    /**
     * Create a new ContextServer using a thread pool.
     * @param pool The thread pool.
     */
    public ContextServer(ThreadPool pool) {
        /*
         * We need both because the client may not support named pipes.
         */
        ctx = new PipeContextServer(this, pool);
        /* socket context server will be created on demand */
        
        this.pool = pool;
    }
    /**
     * Destroy the pipe or socket context server.
     */
    public void destroy() {
        ctx.destroy();
        if(sock!=null) sock.destroy();
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

    private synchronized SocketContextServer getSocketContextServer(ContextServer server, ThreadPool pool) {
	if(sock!=null) return sock;
	return sock=new SocketContextServer(server, pool);
    }

    /**
     * Start a channel name.
     * @param channelName The ChannelName.
     * @throws IllegalStateException if there's no Pipe- or SocketContextServer available
     */
    public void start(IContextServer.ChannelName channelName) {
	boolean started = channelName.start();
	if(!started) throw new IllegalStateException("Pipe- and SocketContextServer not available");
    }
    
    /**
     * Check for a ContextRunner for channelName
     * @param channelName The ChannelName
     * @return The ContextRunner or null.
     */
    public ContextRunner schedule(IContextServer.ChannelName channelName) {
        return channelName.schedule();
    }

    /**
     * Recycle a ContextRunner, if possible.
     * @param channelName The ChannelName.
     */
    public void recycle(IContextServer.ChannelName channelName) {
	channelName.recycle();
    }
    /**
     * Return the channelName which be passed to the client as X_JAVABRIDGE_REDIRECT
     * @param channelName The name of the channel, see X_JAVABRIDGE_CHANNEL
     * @param kontext The name of the client's default ContextFactory, see X_JAVABRIDGE_CONTEXT_DEFAULT
     * @param currentCtx The current ContextFactory, see X_JAVABRIDGE_CONTEXT
     * @return The channel name of the Pipe- or SocketContextServer.
     */
    public IContextServer.ChannelName getFallbackChannelName(String channelName, String kontext, IContextFactory currentCtx) {
        if(channelName!=null && ctx.isAvailable()) return new PipeChannelName(channelName, kontext, currentCtx);
        SocketContextServer sock=getSocketContextServer(this, pool);
        return new SocketChannelName(sock.getChannelName(), kontext, currentCtx);
    }
}
