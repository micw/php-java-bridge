/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class LocalSocket extends Socket {
    private OutputStream ostream;
    private InputStream istream;
	
    public LocalSocket(int peer) {
	this.ostream = new LocalSocketOutputStream(peer);
	this.istream = new LocalSocketInputStream(peer);
    }
    public OutputStream getOutputStream() throws IOException {
	return ostream;
    }
    public InputStream getInputStream() throws IOException {
	return istream;
    }
		
	
}
