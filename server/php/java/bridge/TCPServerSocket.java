/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

class TCPServerSocket implements ISocketFactory {

    public static final String DefaultSocketname = Util.TCP_SOCKETNAME;
    private ServerSocket sock;
    private int port;
    boolean local;
    
    public static ISocketFactory create(String name, int backlog) throws IOException {
	int p;
	boolean local = false;

	if(name==null) name=DefaultSocketname;
	if(name.startsWith("INET:")) name=name.substring(5);
	if(name.startsWith("INET_LOCAL:")) { local = true; name=name.substring(11); }
	else if(name.startsWith("LOCAL:")) return null;
	    
	try {
	    p=Integer.parseInt(name);
	} catch (NumberFormatException e) {
	    p=Integer.parseInt(DefaultSocketname);
	}
	TCPServerSocket s = new TCPServerSocket(p, backlog, local);
	return s;
    }

    private ServerSocket newServerSocket (int port, int backlog) throws java.io.IOException {
	try {
	    if(local)
		return new ServerSocket(port, backlog, InetAddress.getByName("127.0.0.1"));
	} catch (java.net.UnknownHostException e) {/*cannot happen*/}
	return new ServerSocket(port, backlog);
    }

    private void findFreePort(int start, int backlog) {
	for (int port = start; port < start+100; port++) {
	    try {
		this.sock = newServerSocket(port, backlog);
		this.port = port;
		return;
	    } catch (IOException e) {continue;}
	    
	}
    }

    private TCPServerSocket(int port, int backlog, boolean local) throws IOException {
	this.local = local;
	if(port==0) {
	    findFreePort(Integer.parseInt(DefaultSocketname), backlog);
	} else {
	    this.sock = newServerSocket(port, backlog);    
	    this.port = port;
	}
    }
	
    public void close() throws IOException {
	sock.close();
    }

    public Socket accept() throws IOException {
	Socket s = sock.accept();
	s.setTcpNoDelay(true);
 	return s;
    }
    public String getSocketName() {
    	return String.valueOf(port);
    }
    public String toString() {
    	return (local?"INET_LOCAL:":"INET:") +getSocketName();
    }
}
