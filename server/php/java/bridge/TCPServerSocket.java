/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServerSocket implements ISocketFactory {

    private ServerSocket sock;

    public static ISocketFactory create(int port, int backlog) throws IOException {
	return new TCPServerSocket(port, backlog);
    }
    public TCPServerSocket(int port, int backlog)
	throws IOException {
	sock=new ServerSocket(port, backlog);
	JavaBridge.initGlobals(null);
    }
	
    public void close() throws IOException {
	sock.close();
    }

    public Socket accept(JavaBridge bridge) throws IOException {
	Util.logDebug("Request from unknown client");
	return sock.accept();
    }

}
