/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.OutputStream;

class LocalSocketOutputStream extends OutputStream {

    int peer;
    LocalSocket socket;
    public LocalSocketOutputStream(LocalSocket socket, int peer) {
	super();
	this.peer=peer;
	this.socket=socket;
    }

    public void write(int b) throws IOException {
	throw new NotImplementedException();
    }
    public void write(byte b[], int dummy, int len) throws IOException {
	JavaBridge.swrite(peer, b, len);
    }
    public void close() throws IOException {
    	socket.close();
    }

}
