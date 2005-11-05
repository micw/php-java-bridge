/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;

import php.java.bridge.NotImplementedException;
import php.java.bridge.Util;

/**
 * @author jostb
 *
 */
public class HttpResponse {
	
	private HashMap headers;
	private OutputStream outputStream;
	
	private boolean headersWritten;

	/**
	 * @param outputStream
	 */
	public HttpResponse(OutputStream outputStream) {
		this.outputStream = outputStream;
		this.headers = new HashMap();
		this.headersWritten = false;
	}

	/**
	 * @param string
	 * @param id
	 */
	public void setHeader(String string, String id) {
		headers.put(string, id);
	}

	/**
	 * @return
	 */
	public OutputStream getOutputStream() {
		if(!this.headersWritten) throw new IllegalStateException("Use setContentLength() before calling getOutputStream.");
		return outputStream;
	}

	/**
	 * @param i
	 */
	public void setStatus(int i) {
		throw new NotImplementedException();
	}

	/**
	 * @param string
	 * @param string2
	 */
	public void addHeader(String string, String string2) {
		setHeader(string, string2);
	}

	private final byte[] h1 = Util.toBytes("PUT dummy HTTP/1.1\r\n"); 
	private final byte[] h2 = Util.toBytes("Host: localhost\r\n"); 
	private final byte[] h3 = Util.toBytes("\r\n"); 
	private final byte[] h4 = Util.toBytes(":");
	private void writeHeaders() throws IOException {
    	java.io.ByteArrayOutputStream out = new ByteArrayOutputStream();
    	out.write(h1);
    	out.write(h2);
    	for(Iterator ii = headers.keySet().iterator(); ii.hasNext(); ) {
    		Object key = ii.next();
    		Object val = headers.get(key);
    		out.write(Util.toBytes((String)key));
    		out.write(h4);
    		out.write(Util.toBytes((String)val));
    		out.write(h3);
    	}
    	out.write(h3);
    	out.writeTo(outputStream);
	}
	/**
	 * @param i
	 * @throws IOException
	 */
	public void setContentLength(int i) throws IOException {
		setHeader("Content-Length", String.valueOf(i));
		writeHeaders();
		this.headersWritten = true;
	}

}
