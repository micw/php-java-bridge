/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet.fastcgi;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

import php.java.bridge.ILogger;
import php.java.bridge.Util;
import php.java.bridge.Util.Process;
import php.java.servlet.ServletUtil;

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

class SocketChannelFactory extends ChannelFactory {
    public static final String LOCAL_HOST = "127.0.0.1";
    private int port;

    private  ServerSocket fcgiTestSocket = null;
    private  int fcgiTestPort;
    
    public SocketChannelFactory (boolean promiscuous) {
	this.promiscuous = promiscuous;
    }
    public void test() throws ConnectException {
        Socket testSocket;
	try {
	    testSocket = new Socket(InetAddress.getByName(getName()), port);
	    testSocket.close();
	} catch (IOException e) {
	    throw new ConnectException(e);
	}
    }
    /**
     * Create a new socket and connect
     * it to the given host/port
     * @param host The host, for example 127.0.0.1
     * @param port The port, for example 9667
     * @return The socket
     * @throws UnknownHostException
     * @throws ConnectionException
     */
    private Socket doConnect(String host, int port) throws ConnectException {
        Socket s = null;
	try {
            s = new Socket(InetAddress.getByName(host), port);
	} catch (IOException e) {
	    throw new ConnectException(e);
	}
	try {
	    s.setTcpNoDelay(true);
	} catch (SocketException e) {
	    Util.printStackTrace(e);
	}
	return s;
    }

    public Channel connect() throws ConnectException {
	Socket s = doConnect(getName(), getPort());
	return new SocketChannel(s); 	
    }
    
    protected void waitForDaemon() throws UnknownHostException, InterruptedException {
	long T0 = System.currentTimeMillis();
	int count = 15;
	InetAddress addr = InetAddress.getByName(LOCAL_HOST);
	if(Util.logLevel>3) Util.logDebug("Waiting for PHP FastCGI daemon");
	while(count-->0) {
	    try {
		Socket s = new Socket(addr, getPort());
		s.close();
		break;
	    } catch (IOException e) {/*ignore*/}
	    if(System.currentTimeMillis()-16000>T0) break;
	    Thread.sleep(1000);
	}
	if(count==-1) Util.logError("Timeout waiting for PHP FastCGI daemon");
	if(Util.logLevel>3) Util.logDebug("done waiting for PHP FastCGI daemon");
    }
	    
    /* Start a fast CGI Server process on this computer. Switched off per default. */
    protected Process doBind(Map env, String php, boolean includeJava) throws IOException {
	if(proc!=null) return null;
	StringBuffer buf = new StringBuffer((Util.JAVABRIDGE_PROMISCUOUS || promiscuous) ? "" : LOCAL_HOST); // bind to all available or loopback only
	buf.append(':');
	buf.append(String.valueOf(getPort()));
	String port = buf.toString();
	        
	// Set override hosts so that php does not try to start a VM.
	// The value itself doesn't matter, we'll pass the real value
	// via the (HTTP_)X_JAVABRIDGE_OVERRIDE_HOSTS header field
	// later.
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", servlet.override_hosts?"/":"");
	env.put("REDIRECT_STATUS", "200");
	String[] args = Util.getPhpArgs(new String[]{php, "-b", port}, includeJava, ServletUtil.getRealPath(servlet.context, FastCGIServlet.CGI_DIR), ServletUtil.getRealPath(servlet.context, FastCGIServlet.PEAR_DIR), ServletUtil.getRealPath(servlet.context, FastCGIServlet.WEB_INF_DIR));
	File home = null;
	if(php!=null) try { home = ((new File(php)).getParentFile()); } catch (Exception e) {Util.printStackTrace(e);}
	proc = new FCGIProcess(args, home, env, ServletUtil.getRealPath(servlet.context, FastCGIServlet.CGI_DIR), servlet.phpTryOtherLocations, servlet.preferSystemPhp);
	proc.start();
	return (Process)proc;
    }
    public int getPort() {
	return port;
    }
    public String getName() {
	return LOCAL_HOST;
    }
    public String getFcgiStartCommand(String base, String php_fcgi_max_requests) {
	StringBuffer buf = new StringBuffer(".");
	buf.append(File.separator);
	buf.append("php-cgi-");
	buf.append(Util.osArch);
	buf.append("-");
	buf.append(Util.osName);
	String wrapper = buf.toString();
	String msg =null;
	msg=
	    "Please start Apache or IIS or start a standalone PHP server.\n"+
	    "For example with the commands: \n\n" +
	    "cd " + base + "\n" + 
	    "chmod +x " + wrapper + "\n" + 
	    "REDIRECT_STATUS=200 " +
	    "X_JAVABRIDGE_OVERRIDE_HOSTS=\"/\" " +
	    "PHP_FCGI_CHILDREN=\"5\" " +
	    "PHP_FCGI_MAX_REQUESTS=\""+php_fcgi_max_requests+"\" "+wrapper+" -d allow_url_include=On -c "+wrapper+".ini -b 127.0.0.1:" +
	    getPort()+"\n\n";
	return msg;
    }
    protected void bind(ILogger logger) throws InterruptedException, IOException {
	if(fcgiTestSocket!=null) { fcgiTestSocket.close(); fcgiTestSocket=null; }// replace the allocated socket# with the real fcgi server
	super.bind(logger);
    }
		
    public void findFreePort(boolean select) {
	fcgiTestPort=FastCGIServlet.FCGI_PORT; 
	fcgiTestSocket=null;
	for(int i=FastCGIServlet.FCGI_PORT+1; select && (i<FastCGIServlet.FCGI_PORT+100); i++) {
	    try {
		ServerSocket s = new ServerSocket(i, Util.BACKLOG, InetAddress.getByName(LOCAL_HOST));
		fcgiTestPort = i;
		fcgiTestSocket = s;
		break;
	    } catch (IOException e) {/*ignore*/}
	}
    }
    public void setDefaultPort() {
	port = FastCGIServlet.FCGI_PORT;
    }
    protected void setDynamicPort() {
	port = fcgiTestPort;
    }
    public void destroy() {
	super.destroy();
	if(fcgiTestSocket!=null) try { fcgiTestSocket.close(); fcgiTestSocket=null;} catch (Exception e) {/*ignore*/}
    }	    
}