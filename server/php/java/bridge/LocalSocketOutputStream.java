/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.OutputStream;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class LocalSocketOutputStream extends OutputStream {

    int peer;
    public LocalSocketOutputStream(int peer) {
	super();
	this.peer=peer;
    }

    public void write(int b) throws IOException {
	throw new NotImplementedException();
    }
    public void write(byte b[], int dummy, int len) throws IOException {
	JavaBridge.swrite(peer, b, len);
    }

}
