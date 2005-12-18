/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import php.java.bridge.ThreadPool;

/**
 * @author jostb
 *
 */
public class ContextServer {

    PipeContextServer ctx;
    SocketContextServer sock;
    public ContextServer(ThreadPool pool) {
        /*
         * We need both because the client may not support named pipes.
         */
        ctx = new PipeContextServer(this, pool);
        sock = new SocketContextServer(this, pool);
    }
    
    private int runnables = 0;    
    /**
     * Get the next runnable
     * @return True if there is a runnable in the queue, false otherwise.
      */
    public final synchronized boolean getNext() {
	if(runnables==0) return false;
	runnables--;
	return true;
    }

    /**
     * Add a runnable to the queue.
     * @see ContextServer#getNext()
     */
    public final synchronized void schedule() {
	runnables++;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#destroy()
     */
    public void destroy() {
        ctx.destroy();
        sock.destroy();
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#getChannelName()
     */
    public String getChannelName() {
        String name = ctx.getChannelName();
        if(name==null) name = sock.getChannelName();
        return name;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#isAvailable()
     */
    public boolean isAvailable() {
        return ctx.isAvailable() || sock.isAvailable();
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#start(java.lang.String)
     */
    public void start() {
        boolean started = ctx.start() ||  sock.start();
        if(!started) throw new IllegalStateException("Pipe- and SocketRunner not available");
    }
    
    public void setChannel(String channel) {
        if("".equals(channel)) channel = null;
        ctx.setChannelName(channel);
        sock.setChannelName(channel);
    }
}
