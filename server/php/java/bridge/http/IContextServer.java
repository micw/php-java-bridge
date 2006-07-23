/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The interface that all ContextServer must implement.
 * 
 * @author jostb
 */
public interface IContextServer {
     /**
     * Destroy the server
     *
     */
    public void destroy();

    /**
     * Check if the ContextServer is ready, i.e. it has created a server socket.
     * @return true if there's a server socket listening, false otherwise.
     */
    public boolean isAvailable();
    
    /**
     * Represents the pipe or socket channel.
     * @author jostb
     *
     */
    public static abstract class Channel {
        /**
         * Returns the channel's input stream.
         * @return The InputStream
         * @throws FileNotFoundException
         */
        public abstract InputStream getInputStream() throws FileNotFoundException;
        /**
         * Returns the channel's output stream.
         * @return The OutputStream.
         * @throws FileNotFoundException
         */
        public abstract OutputStream getOuptutStream() throws FileNotFoundException;
        /**
         * Shut down the channel, closes the in- and output stream and other resources.
         */
        public abstract void shutdown();
        
        /**
         * Returns the name of the channel, for example the socket # or the pipe name.
         * @see php.java.bridge.http.IContextServer.ChannelName#getDefaultName()
         * @return the name of the channel.
         */
        public abstract String getName();
    }

    /**
     * Represents the pipe or socket channel name.
     * @author jostb
     */
    public static abstract class ChannelName {
        protected String name, kontext;
        protected ContextRunner runner;
        protected IContextFactory currentCtx;
        
        /**
         * Create a new ChannelName. 
         * @param name The name of the channel, see X_JAVABRIDGE_CHANNEL
         * @param kontext The name of client's default context, see X_JAVABRIDGE_CONTEXT_DEFAULT
         * @param currentCtx The ContextFactory associated with the current request.
         */
        public ChannelName(String name, String kontext, IContextFactory currentCtx) {
            this.name = name;
            this.kontext = kontext;
            this.currentCtx = currentCtx;
        }
        /**
         * Returns the name of the channel, for example the socket # or the pipe name.
         * @return the name of the channel
         */
        public String getName() {
            return name;
        }
        
        /**
         * Returns the default (persistent) name of the channel, if it exists. Otherwise it returns the name.
         * @return the persistent name of the channel from X_JAVABRIDGE_CONTEXT_DEFAULT.
         * @see #getName()
         */
        public String getDefaultName() {
            if(runner!=null) return runner.getChannel().getName();
            return name;
        }
        /**
         * Check if the default name exists, see X_JAVABRIDGE_CONTEXT_DEFAULT
         * @return true, if the persistent name exists, false otherwise.
         */
        public boolean hasDefault() {
            return runner!=null;
        }
        /**
         * Return the 
         * @return the value for X_JAVABRIDGE_CONTEXT.
         */
        public IContextFactory getCtx() {
            return currentCtx;
        }
        /**
         * @return the value for X_JAVABRIDGE_CONTEXT_DEFAULT.
         * @see #getDefaultName() 
         */
        public String getKontext() {
            return kontext;
        }
        /**
         * Start the channel. This method calls IContextServer.start()
         */
        protected abstract boolean startChannel();
        
        /**
         * Check if there's a persistent ContextRunner available for the kontext.
         * After calling this method the persistent name can be queried with 
         * {@link #getDefaultName()} and
         * {@link #hasDefault()}.
         * @return the default name or null.
         */
        public ContextRunner schedule() {
            return runner = ContextRunner.checkRunner(this);
        }
        /**
         * Recycle a ContextRunner for a new ContextServer. It checks if a ContextRunner is available for the 
         * default ContextFactory and updates the ContextRunner with the JavaBridge from the fresh ContextFactory.
         * The new JavaBridge and ContextFactory instance are destroyed, when the request is done.
         */
	public void recycle() {
	    if(runner != null) 
		runner.recycle(currentCtx);
	}
	/**
	 * Start a new ContextRunner for a given ContextServer. The current ContextFactory becomes the 
	 * default for this runner.
	 * @return true, if the channel is available, false otherwise.
	 */
	public boolean start() {
	    if(runner == null) 
		return startChannel();
	    return true;
	}
    }

    /**
     * Start the runner.
     * @param channel The channel name
     */
    public boolean start(ChannelName channel);
}
