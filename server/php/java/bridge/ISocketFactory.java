/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.Socket;

/**
 * Create and destroy socket instances.
 * @author jostb
 *
 */
public interface ISocketFactory {
    
    /**
     * Close the socket instance.
     * @throws IOException
     */
    public void close() throws IOException;
    
    /**
     * Create a new socket instance.
     * @return The new communication socket.
     * @throws IOException
     */
    public Socket accept() throws IOException;
    
    /**
     * Return the socket# as a string. 
     * @return The socket number as a string.
     */
    public String getSocketName();
}
