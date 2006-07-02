/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.net.Socket;

class LocalServerSocket implements ISocketFactory {

    public static final String DefaultSocketname = "/var/run/.php-java-bride_socket";
    private String name;
    private int peer;
	
    public static ISocketFactory create(int logLevel, String name, int backlog) throws IOException {
	if(name==null) name=DefaultSocketname;
	if(name.startsWith("INET:") || name.startsWith("INET_LOCAL:")) return null;

	return new LocalServerSocket(logLevel, name==null?DefaultSocketname:name, backlog);
    }
    private LocalServerSocket(int logLevel, String name, int backlog)
	throws IOException {
	if(name.startsWith("LOCAL:")) name=name.substring(6);
	this.name=name;
	if(0==(this.peer=JavaBridge.startNative(logLevel, backlog, name))) throw new IOException("Unix domain sockets not available.");
		
    }
    public void close() throws IOException {
	JavaBridge.sclose(this.peer);
    }

    public Socket accept() throws IOException {
	int peer = JavaBridge.accept(this.peer);
	return new LocalSocket(peer);
    }
    public String getSocketName() {
    	return name;
    }
    public String toString() {
    	return "LOCAL:" +getSocketName();
    }
}
