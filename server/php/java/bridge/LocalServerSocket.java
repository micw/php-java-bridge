/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.File;
import java.io.IOException;
import java.net.Socket;

public class LocalServerSocket implements ISocketFactory {

    public static final String DefaultSocketname = "/var/run/.php-java-bride_socket";
    private int backlog;
    private String name;
    private int peer;
	
    public static ISocketFactory create(String name, int backlog) throws IOException {
	return new LocalServerSocket(name==null?DefaultSocketname:name, backlog);
    }
    private LocalServerSocket(String name, int backlog)
	throws IOException {
	if(name==null) name=DefaultSocketname;
	if(name.startsWith("LOCAL:")) name=name.substring(6);
    	else if(name.startsWith("INET:")) 
	    throw new IOException("local socketname must start with 'LOCAL:'");
	this.backlog=backlog;
	this.name=name;
	if(0==(this.peer=JavaBridge.startNative(Util.logLevel, backlog, name))) throw new IOException("Unix domain sockets not available.");
		
    }
    public void close() throws IOException {
	JavaBridge.sclose(this.peer);
    }

    public Socket accept(JavaBridge bridge) throws IOException {
	int peer = JavaBridge.accept(this.peer);
	Util.logDebug("Request from client with uid/gid "+bridge.uid+"/"+bridge.gid);
	return new LocalSocket(peer);
    }
    public String getSocketName() {
    	return name;
    }
    public String toString() {
    	return "LOCAL:" +getSocketName();
    }
}
