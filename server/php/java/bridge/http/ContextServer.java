/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import php.java.bridge.ThreadPool;

/**
 * A bridge pattern which either uses the PipeContextServer or the SocketContextServer, 
 * depending on the OS and/or the security restrictions.
 * @author jostb
 * @see php.java.bridge.http.PipeContextServer
 * @see php.java.bridge.http.SocketContextServer
 */
public class ContextServer {

    PipeContextServer ctx;
    SocketContextServer sock;
    
    public abstract class ChannelName {
        protected String name;
        public ChannelName(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public abstract boolean startChannel();
    }
    private class PipeChannelName extends ChannelName {
        public PipeChannelName(String name) {super(name);}

        /* (non-Javadoc)
         * @see php.java.bridge.http.ContextServer.ChannelName#start()
         */
        public boolean startChannel() {
            return ctx.start(name);
        }
         public String toString() {
            return "Pipe:"+name;
        }
    }
    private class SocketChannelName extends ChannelName {
        public SocketChannelName(String name) {super(name);}
        
        /* (non-Javadoc)
         * @see php.java.bridge.http.ContextServer.ChannelName#start()
         */
        public boolean startChannel() {
            return sock.start(name);
        }
        public String toString() {
            return "Socket:"+name;
        }
    }
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
     * @see php.java.bridge.http.IContextServer#isAvailable()
     */
    public boolean isAvailable() {
        return ctx.isAvailable() || sock.isAvailable();
    }

    /* (non-Javadoc)
     * @see php.java.bridge.http.IContextServer#start(java.lang.String)
     */
    public void start(ChannelName channelName) {
        if(channelName == null) throw new NullPointerException("channelName");
        boolean started = channelName.startChannel();
        if(!started) throw new IllegalStateException("Pipe- and SocketRunner not available");
    }
    
    public ChannelName getFallbackChannelName(String channelName) {
        if(channelName!=null && channelName.length()!=0 && ctx.isAvailable()) return new PipeChannelName(channelName);
        return new SocketChannelName(sock.getChannelName());
    }
}
