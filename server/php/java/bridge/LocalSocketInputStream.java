/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class LocalSocketInputStream extends InputStream {

    int peer;
    public LocalSocketInputStream(int peer) {
	super();
	this.peer=peer;
    }

    public int read() throws IOException {
	throw new NotImplementedException();
    }
    public int read(byte b[], int dummy, int len) throws IOException {
	return JavaBridge.sread(peer, b, len);
    }

}
