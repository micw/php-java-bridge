/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author jostb
 *
 */
public interface IContextServer {
     /**
     * Destroy the server
     *
     */
    public void destroy();

    /**
     * Returns the channel name
     * @return The channel name.
     */
    public String getChannelName();

    /**
     * Sets the communication channel.
     * @param channelName The channel name, e.g.: /tmp/pipe or /dev/shm/channel
     */
    public void setChannelName(String channelName);

    /**
     * Check if the ContextServer is ready, i.e. it has created a server socket.
     * @return true if there's a server socket listening, false otherwise.
     * @see ContextServer#getSocket()
     */
    public boolean isAvailable();
    
    /**
     * Represents he pipe or socket channel.
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
         *
         */
        public abstract void shutdown();
        }

    /**
     * Start the runner.
     */
    public boolean start();    
}