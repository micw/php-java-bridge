/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServerSocket implements ISocketFactory {

    public static final String DefaultSocketname = "9167";
    private ServerSocket sock;
    private int port;
    
    public static ISocketFactory create(String name, int backlog) throws IOException {
	return new TCPServerSocket(Integer.parseInt(name==null?DefaultSocketname:name), backlog);
    }
    private TCPServerSocket(int port, int backlog)
	throws IOException {
    	this.port=port;
	this.sock=new ServerSocket(port, backlog);
	JavaBridge.initGlobals(null);
    }
	
    public void close() throws IOException {
	sock.close();
    }

    public Socket accept(JavaBridge bridge) throws IOException {
	Socket s = sock.accept();
    	Util.logDebug("Request from unknown client");
	return s;
    }
    public String getSocketName() {
    	return String.valueOf(port);
    }
    public String toString() {
    	return "INET: " +getSocketName();
    }
}
