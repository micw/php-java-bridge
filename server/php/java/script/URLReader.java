/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Socket;
import java.net.URL;
import java.util.Hashtable;

import php.java.bridge.SessionFactory;
import php.java.bridge.Util;

/**
 * @author jostb
 *
 */
public class URLReader extends Reader {

	private URL url;

	public URLReader(URL url) {
		this.url = url;
	}
	
	public URL getURL() {
		return url;
	}
	/* (non-Javadoc)
	 * @see java.io.Reader#read(char[], int, int)
	 */
	public int read(char[] cbuf, int off, int len) throws IOException {
		close();
		return 0;
	}
	public void read(HttpProxy kont, Hashtable env, SessionFactory ctx, OutputStream out) throws IOException {

	InputStream in;
	OutputStream natOut;
	Socket socket;
    	int c;
 	    byte[] buf = new byte[Util.BUF_SIZE];
	    socket = new Socket(url.getHost(), url.getPort());
	    natOut = socket.getOutputStream();
	    natOut.write(Util.toBytes("GET "+url.getFile()+" HTTP/1.1\r\n"));
	    natOut.write(Util.toBytes("Host: " + url.getHost()+ "\r\n"));
	    natOut.write(Util.toBytes("X_JAVABRIDGE_CONTEXT: " +env.get("X_JAVABRIDGE_CONTEXT")+"\r\n"));
	    natOut.write(Util.toBytes("X_JAVABRIDGE_OVERRIDE_HOSTS:" + env.get("X_JAVABRIDGE_OVERRIDE_HOSTS")+"\r\n"));
	    natOut.write(Util.toBytes("X_JAVABRIDGE_CONTINUATION:" + env.get("X_JAVABRIDGE_CONTINUATION")+"\r\n"));
	    natOut.write(Util.toBytes("Content-Length: 0\r\n"));
	    natOut.write(Util.toBytes("Connection: close" + "\r\n\r\n"));
	    in = socket.getInputStream();
	    Util.parseBody(buf, in, out);
	    natOut.close();
	    in.close();
	    socket.close();	    			
	}
	/* (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	public void close() throws IOException {
		throw new IllegalStateException("Use urlReader.read(HttpProxy kont, ...) or use a FileReader() instead.");
	}
}
