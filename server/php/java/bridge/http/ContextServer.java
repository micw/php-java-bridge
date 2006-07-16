/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import php.java.bridge.ThreadPool;

/**
 * A bridge pattern which either uses the PipeContextServer or the SocketContextServer, 
 * depending on the OS and/or the security restrictions.
 * 
 * There can be more than one ContextServer instance per classloader, but the ContextFactory.get() checks
 * if it is called with the same ContextServer and throws a SecurityException otherwise. 
 * So one cannot access contexts belonging to other ContextServers.
 *  
 * @author jostb
 * @see php.java.bridge.http.PipeContextServer
 * @see php.java.bridge.http.SocketContextServer
 */
public final class ContextServer {
    private PipeContextServer ctx;
    private static SocketContextServer sock = null;
    private ThreadPool pool;
    
    private class PipeChannelName extends IContextServer.ChannelName {
        public PipeChannelName(String name, String kontext, IContextFactory ctx) {super(name, kontext, ctx);}

        public boolean startChannel() {
            return ctx.start(this);
        }
        public String schedule() {
            return defaultName = ctx.schedule(this);
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
        public String schedule() {
            return sock.schedule(this);
        }
        public String toString() {
            return "Socket:"+getDefaultName();
        }
    }
    public ContextServer(ThreadPool pool) {
        /*
         * We need both because the client may not support named pipes.
         */
        ctx = new PipeContextServer(this, pool);
        /* socket context server will be created on demand */
        
        this.pool = pool;
    }
    
    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#destroy()
     */
    public void destroy() {
        ctx.destroy();
        if(sock!=null) sock.destroy();
    }


    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#isAvailable()
     */
    public boolean isAvailable(String channelName) {
        if(channelName!=null && ctx.isAvailable()) return true;
        SocketContextServer sock=getSocketContextServer(this, pool);
        return sock!=null && sock.isAvailable();
    }

    private static synchronized SocketContextServer getSocketContextServer(ContextServer server, ThreadPool pool) {
	if(sock!=null) return sock;
	return sock=new SocketContextServer(server, pool);
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#start(java.lang.String)
     */
    public void start(IContextServer.ChannelName channelName) {
	ContextRunner runner = channelName.getRunner();
	if(runner != null) {
	    runner.recycle(channelName.getCtx(), this);	    
	} else {
	    boolean started = channelName.startChannel();
	    if(!started) throw new IllegalStateException("Pipe- and SocketRunner not available");
	}
    }
    
    public String schedule(IContextServer.ChannelName channelName) {
        if(channelName == null) throw new NullPointerException("channelName");
        return channelName.schedule();
    }

    public IContextServer.ChannelName getFallbackChannelName(String channelName, String kontext, IContextFactory currentCtx) {
        if(channelName!=null && ctx.isAvailable()) return new PipeChannelName(channelName, kontext, currentCtx);
        SocketContextServer sock=getSocketContextServer(this, pool);
        return new SocketChannelName(sock.getChannelName(), kontext, currentCtx);
    }
}
