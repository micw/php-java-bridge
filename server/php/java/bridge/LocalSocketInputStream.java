/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;

class LocalSocketInputStream extends InputStream {

    int peer;
    LocalSocket socket;
    
    public LocalSocketInputStream(LocalSocket socket, int peer) {
	super();
	this.peer=peer;
	this.socket=socket;
    }

    public int read() throws IOException {
	throw new NotImplementedException();
    }
    public int read(byte b[], int dummy, int len) throws IOException {
	return JavaBridge.sread(peer, b, len);
    }

    public void close() throws IOException {
    	socket.close();
    }

}
