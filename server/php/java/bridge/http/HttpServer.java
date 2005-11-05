/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

import php.java.bridge.ISocketFactory;
import php.java.bridge.Util;


/**
 * @author jostb
 *
 */
public abstract class HttpServer implements Runnable {
	protected ISocketFactory socket;
	
	public abstract ISocketFactory bind();
	
	public HttpServer() {
		socket = bind();
		Thread t = new Thread(this, "JavaBridgeHttpServer");
		t.setDaemon(true);
        t.start();
	}

	public void parseHeader(HttpRequest req) throws UnsupportedEncodingException, IOException {
		byte buf[] = new byte[Util.BUF_SIZE];
		
		InputStream natIn = req.getInputStream();
    		String line = null;
    		int i=0, n, s=0;
    		boolean eoh=false;
    		// the header and content
    		while((n = natIn.read(buf, i, buf.length-i)) !=-1 ) {
    		    int N = i + n;
    		    // header
    		    while(!eoh && i<N) {
    			switch(buf[i++]) {
    			
    			case '\n':
    			    if(s+2==i && buf[s]=='\r') {
    				eoh=true;
    			    } else {
    			    	req.addHeader(new String(buf, s, i-s-2, "ASCII"));
    				s=i;
    			    }
    			}
    		    }
    		    // body
    		    if(eoh) {
    			req.pushBack(buf, i, N-i);
    			break;
    		    }
    		}

        }
		

	protected void doRun() throws IOException {
	    while(true) {
			Socket sock;
			try {sock = socket.accept();} catch (IOException e) {return;} // socket closed
	                Util.logDebug("Socket connection accepted");
	                HttpRequest req = new HttpRequest(sock.getInputStream());
	                HttpResponse res = new HttpResponse(sock.getOutputStream());
	                parseHeader(req);
	                parseBody(req, res);
		    }
	}

	/**
	 * @param req
	 * @param res
	 */
	protected void parseBody(HttpRequest req, HttpResponse res) {
		req.setContentLength(Integer.parseInt(req.getHeader("Content-Length")));
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			doRun();
		} catch (IOException e) {
			Util.printStackTrace(e);
		}
	}
	
	public void destroy() {
		try {
			socket.close();
		} catch (IOException e) {
			Util.printStackTrace(e);
		}
	}
}
