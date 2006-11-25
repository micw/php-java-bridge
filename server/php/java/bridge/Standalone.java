/*-*- mode: Java; tab-width:8 -*-*/
package php.java.bridge;

/*
 * Copyright (C) 2006 Jost Boekemeier
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

import java.io.File;
import java.io.IOException;

/**
 * This is the standalone container of the PHP/Java Bridge. It starts
 * the standalone back-end, listenes for protocol requests and handles
 * CreateInstance, GetSetProp and Invoke requests. Supported protocol
 * modes are INET (listens on all interfaces), INET_LOCAL (loopback
 * only), LOCAL (uses a local, invisible communication channel,
 * requires natcJavaBridge.so), SERVLET and SERVLET_LOCAL 
 * (starts the built-in servlet engine listening on all interfaces or loopback).  
 * <p> Example:<br> <code> java
 * -Djava.awt.headless=true -jar JavaBridge.jar INET_LOCAL:9676 5
 * bridge.log &<br> telnet localhost 9676<br> &lt;CreateInstance
 * value="java.lang.Long" predicate="Instance" id="0"&gt;<br> &lt;Long
 * value="6"/&gt; <br> &lt;/CreateInstance&gt;<br> &lt;Invoke
 * value="1" method="toString" predicate="Invoke" id="0"/&gt;<br>
 * </code>
 *
 */

public class Standalone {

    /**
     * Create a new server socket and return it. This procedure should only
     * be used at boot time. Use JavaBridge.bind instead.
     * @param logLevel the current logLevel
     * @param sockname the socket name
     * @return the server socket
     * @throws IOException 
     */
    static ISocketFactory bind(int logLevel, String sockname) throws IOException {
	ISocketFactory socket = null;
	try {
	    socket = LocalServerSocket.create(logLevel, sockname, Util.BACKLOG);
	} catch (Throwable e) {
	    try {
	    // do not access Util at this point, static final fields are an exception.
	    boolean promiscuous = System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true");
	    socket = TCPServerSocket.create(promiscuous?"INET:0":"INET_LOCAL:0", Util.BACKLOG);
	    } catch(Throwable t) {/*ignore*/}
	}
	if(null==socket)
	    socket = TCPServerSocket.create(sockname, Util.BACKLOG);

	if(null==socket)
	    throw new IOException("Could not create socket: " + sockname);

	return socket;
    }
    private static void monoUsage() {
	System.err.println("PHP/Mono+NET Bridge version "+Util.VERSION);
	System.err.println("Usage: MonoBridge.exe [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("Example: MonoBridge.exe");
	System.err.println("Example: MonoBridge.exe INET_LOCAL:0 3 MonoBridge.log");
    }
    protected void javaUsage() {
	System.err.println("PHP/Java Bridge version "+Util.VERSION);
	System.err.println("Usage: java -jar JavaBridge.jar [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("Usage: java -jar JavaBridge.jar --convert PHP_INCLUDE_DIR [JARFILES]");
	System.err.println("SOCKETNAME is one of LOCAL, INET_LOCAL, INET, SERVLET_LOCAL, SERVLET");
	System.err.println("Example: java -jar JavaBridge.jar");
	System.err.println("Example: java -jar JavaBridge.jar LOCAL:/tmp/javabridge_native.socket 3 /var/log/php-java-bridge.log");
	System.err.println("Example: java -Djava.awt.headless=\"true\" -Dphp.java.bridge.threads=50 -jar JavaBridge.jar INET:9267 3 JavaBridge.log");
	System.err.println("Example: java -jar JavaBridge.jar SERVLET:8080 3 JavaBridge.log");
	System.err.println("Example: java -jar JavaBridge.jar --convert /usr/share/pear lucene.jar ...");
    }
    protected void usage() {
	if(Util.IS_MONO)
	    monoUsage();
	else
	    javaUsage();
	
	System.exit(1);
    }

    protected void checkOption(String s[]) {
	if("--convert".equals(s[0])) {
	    try {
		StringBuffer buf=new StringBuffer();
		for(int i=2; i<s.length; i++) {
		    buf.append(s[i]);
		    if(i+1<s.length) buf.append(File.pathSeparatorChar);
		}
            
		int length = s.length >= 3 ? 2 :s.length-1;
		String str[] = new String[length];
		if(length==2) str[1] = buf.toString();
		if(length>=1) str[0] = s[1];
		Snarf.main(str);
		System.exit(0);
	    } catch (Exception e) {
		e.printStackTrace();
		System.exit(1);
	    }
	} else if ("--version".equals(s[0])) {
	    System.out.println(Util.VERSION);
	    System.exit(0);
	}
	usage();
    }
    /**
     * Global init. Redirects System.out and System.err to the server
     * log file(s) or to System.err and creates and opens the
     * communcation channel. Note: Do not write anything to
     * System.out, this stream is connected with a pipe which waits
     * for the channel name.
     * @param s an array of [socketname, level, logFile]
     */
    protected void init(String s[]) {
	String sockname=null;
	int logLevel = -1;
	if(s.length>3) checkOption(s);
	try {
	    if(s.length>0) {
		sockname=s[0];
		if(sockname.startsWith("-")) checkOption(s);
	    }
	    try {
		if(s.length>1) {
		    logLevel=Integer.parseInt(s[1]);
		}
	    } catch (NumberFormatException e) {
		usage();
	    } catch (Throwable t) {
		t.printStackTrace();
	    }
	    checkServlet(logLevel, sockname, s);
	    ISocketFactory socket = bind(logLevel, sockname);
	    StringBuffer buf = new StringBuffer();
	    buf.append('@');
	    buf.append(socket.getSocketName());
	    buf.append('\n');
	    if(s.length>1) {
		System.out.write(buf.toString().getBytes());
		System.out.close(); 
	    }
	    if("true".equals(System.getProperty("php.java.bridge.test.startup"))) System.exit(0);
	    JavaBridge.initLog(String.valueOf(socket), logLevel, s);
	    JavaBridge.init(socket, logLevel, s);
	} catch (RuntimeException e) { throw e; } 
	catch (Throwable ex) { throw new RuntimeException(ex); }
    }

    private static void checkServlet(int logLevel, String sockname, String[] s) throws InterruptedException, IOException {
	if(sockname==null) return;
	if(sockname.startsWith("SERVLET_LOCAL:")) System.setProperty("php.java.bridge.promiscuous", "false");
	else if(sockname.startsWith("SERVLET:"))  System.setProperty("php.java.bridge.promiscuous", "true");
	else return;
	
	JavaBridge.initLog(sockname, logLevel, s);
	sockname=sockname.substring(sockname.indexOf(':')+1);
	JavaBridgeRunner.main(new String[] {sockname});
	return;
    }
    /**
     * Start the PHP/Java Bridge. <br>
     * Example:<br>
     * <code>java -Djava.awt.headless=true -jar JavaBridge.jar INET:9656 5 /var/log/php-java-bridge.log</code><br>
     * Note: Do not write anything to System.out, this
     * stream is connected with a pipe which waits for the channel name.
     * @param s an array of [socketname, level, logFile]
     */
    public static void main(String s[]) {
	try {
	    System.loadLibrary("natcJavaBridge");
	} catch (Throwable t) {/*ignore*/}
	try {
	    (new Standalone()).init(s);
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(9);
	}
    }
}
