/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import php.java.bridge.Util;

/**
 * This class can be used to connect to a HTTP server to allocate and to invoke php scripts.
 * Example:<p>
 * <code>
 * PhpScriptEngine e = new PhpScriptEngine();<br>
 * e.eval(new URLConnection(new URL("http://localhost:80/foo.php"));<br>
 * System.out.println(((Invocable)e).invoke("java_get_server_name", new Object[]{}));<br>
 * e.release();<br>
 * </code>
 * @author jostb
 *
 */
public class URLReader extends Reader {

    private URL url;
    private Socket socket;

    /**
     * Create a special reader which can be used to read data from a URL.
     * @param url
     * @throws IOException
     * @throws UnknownHostException
     */
    public URLReader(URL url) throws UnknownHostException, IOException {
	this.url = url;
	this.socket = new Socket(url.getHost(), url.getPort());
    }
	
    /**
     * Returns the URL to which this reader connects.
     * @return the URL.
     */
    public URL getURL() {
	return url;
    }

    /**
     * @throws NotImplementedException
     * @see URLReader#read(Map, OutputStream)
     */
    public int read(char[] cbuf, int off, int len) throws IOException {
	close();
	return 0;
    }
	
    /**
     * Read from the URL and write the data to out.
     * @param env The environment, must contain values for X_JAVABRIDGE_CONTEXT. It may contain X_JAVABRIDGE_OVERRIDE_HOSTS.
     * @param out The OutputStream.
     * @throws IOException
     */
    public void read(Map env, OutputStream out) throws IOException {
	InputStream in = null;
	OutputStream natOut = null;
	
	try {
	    String overrideHosts = (String) env.get("X_JAVABRIDGE_OVERRIDE_HOSTS");
	    byte[] buf = new byte[Util.BUF_SIZE];
	    
	    natOut = new java.io.BufferedOutputStream(socket.getOutputStream());
	    natOut.write(Util.toBytes("GET "+url.getFile()+" HTTP/1.1\r\n"));
	    natOut.write(Util.toBytes("Host: " + url.getHost()+":"+url.getPort()+ "\r\n"));
	    natOut.write(Util.toBytes("X_JAVABRIDGE_CONTEXT: " +env.get("X_JAVABRIDGE_CONTEXT")+"\r\n"));
	    if(overrideHosts!=null) {
	        natOut.write(Util.toBytes("X_JAVABRIDGE_OVERRIDE_HOSTS:" + overrideHosts+"\r\n"));
	        // workaround for a problem in php (it confuses the OVERRIDE_HOSTS from the environment with OVERRIDE_HOSTS from the request meta-data 
	        natOut.write(Util.toBytes("X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT:" + overrideHosts+"\r\n"));
	    }
	    natOut.write(Util.toBytes("Content-Length: 0\r\n"));
	    natOut.write(Util.toBytes("Connection: close" + "\r\n\r\n"));
	    natOut.flush();
	    in = socket.getInputStream();
	    Util.parseBody(buf, in, out, Util.DEFAULT_HEADER_PARSER);
	} catch (IOException x) {
	    Util.printStackTrace(x);
	    throw x;
	} finally {
	    if(natOut!=null) try { natOut.close(); } catch (IOException e) {/*ignore*/}
	    if(in!=null) try { in.close(); } catch (IOException e) {/*ignore*/}
	    if(socket!=null) try {socket.close(); } catch (IOException e) {/*ignore*/}
	}
    }
    
    /**
    * @throws NotImplementedException
    * @see URLReader#read(Map, OutputStream)
    */
    public void close() throws IOException {
	throw new IllegalStateException("Use urlReader.read(Hashtable, OutputStream) or use a FileReader() instead.");
    }
    
    /**{@inheritDoc}*/
    public String toString() {
        return String.valueOf(url);
    }
}
