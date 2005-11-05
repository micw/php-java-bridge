/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import php.java.bridge.NotImplementedException;

/**
 * @author jostb
 *
 */
public class HttpRequest {
	private HashMap headers;
	private InputStream in;

	private byte [] buf;
	private int	bufStart = 0;
	private int	bufEnd = 0;

	private int	contentLength = -1;
	private int count = 0;
	
	/**
	 * @param inputStream
	 */
	public HttpRequest(InputStream inputStream) {
		in = new HttpInputStream(inputStream);
		headers = new HashMap();
	}

	/**
	 * @param string
	 * @return
	 */
	public String getHeader(String string) {
		return (String) headers.get(string);
	}

	/**
	 * @return
	 */
	public InputStream getInputStream() {
		return in; 
	}

	/**
	 * @param buf
	 * @param start
	 * @param length
	 */
	public void pushBack(byte[] buf, int start, int length) {
		this.buf = buf;
		this.bufStart = start;
		this.bufEnd = length+start;
	}

	private class HttpInputStream extends InputStream {

		private InputStream in;
		
		
		public HttpInputStream (InputStream in) {
			this.in = in;
		}
		
		/* (non-Javadoc)
		 * @see java.io.InputStream#read()
		 */
		public int read() throws IOException {
			throw new NotImplementedException();
		}
		
		public int read(byte[] b, int start, int length) throws IOException {
			if(count==contentLength) return -1;
			
			if(bufStart!=bufEnd) {
				if(bufEnd - bufStart < length) length = bufEnd - bufStart;
				System.arraycopy(buf, bufStart, b, start, length);
				bufStart += length;
				count += length;
				return length;
			}
			int n = in.read(b, start, length);
			count += n;
			return n;
		}
		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}
	}

	/**
	 * @param string
	 */
	public void addHeader(String line) {
		try {
        headers.put
        (line.substring(0, line.indexOf(":")).trim(),
         line.substring(line.indexOf(":") + 1).trim());
}
 catch (Exception e) {/*not a valid header*/}

	}

	/**
	 * @param header
	 */
	public void setContentLength(int contentLength) {
		this.count = 0;
		this.contentLength = contentLength;
	}
}
