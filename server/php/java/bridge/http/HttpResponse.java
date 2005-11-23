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
 * A simple HTTP response implementation.
 * @author jostb
 *
 */
public class HttpResponse {
	
    private HashMap headers;
    private OutputStream outputStream;
	
    private boolean headersWritten;

    /**
     * Create a new HTTP response with the given OutputStream
     * @param outputStream The OutputStream.
     */
    public HttpResponse(OutputStream outputStream) {
	this.outputStream = outputStream;
	this.headers = new HashMap();
	this.headersWritten = false;
    }

    /**
     * Set the response header
     * @param string The header key
     * @param val The header value.
     */
    public void setHeader(String string, String val) {
	headers.put(string, val);
    }

    /**
     * Returns the OutputStream of the response. setContentLength() must be called before.
     * @return The OutputStream
     * @see HttpRequest#setContentLength(int)
     */
    public OutputStream getOutputStream() {
	if(!this.headersWritten) throw new IllegalStateException("Use setContentLength() before calling getOutputStream.");
	return outputStream;
    }

    /**
     * Set the response status. Not implemented.
     * @param code
     */
    public void setStatus(int code) {
	throw new NotImplementedException();
    }

    /**
     * Add a response header, in this implementation identical to setHeader
     * @param string The header
     * @param string2 The header value
     * @see HttpResponse#setHeader(String, String)
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
     * Set the content length of the response. Sets the "Content-Length" header value.
     * @param length The content length
     * @throws IOException
     * @see HttpResponse#getOutputStream()
     */
    public void setContentLength(int length) throws IOException {
	setHeader("Content-Length", String.valueOf(length));
	writeHeaders();
	this.headersWritten = true;
    }

}
