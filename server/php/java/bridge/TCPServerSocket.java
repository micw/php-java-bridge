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
	int p;
	if(name==null) name=DefaultSocketname;
	if(name.startsWith("INET:")) name=name.substring(5);
	else if(name.startsWith("LOCAL:")) 
	    throw new IOException("tcp socketname must start with 'INET:'");
	try {
	    p=Integer.parseInt(name);
	} catch (NumberFormatException e) {
	    Util.logError("Could not parse TCP socket number: " + e + ". Using default: " + DefaultSocketname);
	}
	return new TCPServerSocket(p, backlog);
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
    	return "INET:" +getSocketName();
    }
}
