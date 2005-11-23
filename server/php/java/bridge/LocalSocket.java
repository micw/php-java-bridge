/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

class LocalSocket extends Socket {

    private OutputStream ostream;
    private InputStream istream;
    private boolean closed=true;
    private int peer;
	
    public LocalSocket(int peer) {
    	closed=false;
    	this.peer=peer;
	this.ostream = new LocalSocketOutputStream(this, peer);
	this.istream = new LocalSocketInputStream(this, peer);
    }
    public OutputStream getOutputStream() throws IOException {
	return ostream;
    }
    public InputStream getInputStream() throws IOException {
	return istream;
    }
    public synchronized void close() throws IOException {
	synchronized(this) {
	    if (closed) return;
	    closed = true;
	    JavaBridge.sclose(peer);
	}
    }
		
	
}
