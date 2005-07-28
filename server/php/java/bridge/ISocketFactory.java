/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.Socket;


public interface ISocketFactory {
    public void close() throws IOException;
    public Socket accept() throws IOException;
    /*
     * Return the socket# as a string. 
     */
    public String getSocketName();
}
