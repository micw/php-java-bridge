/*-*- mode: Java; tab-width:8 -*-*/
package php.java.bridge;

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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.swing.JOptionPane;

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

    /** The default HTTP port for management clients */
    public static final int HTTP_PORT_BASE = 8080;

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
    protected static void disclaimer() {
	System.err.println("Copyright (C) 2003, 2006 Jost Boekemeier and others.");
	System.err.println("This is free software; see the source for copying conditions.  There is NO");
	System.err.println("warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
    }
    private static void monoUsage() {
	System.err.println("PHP/Mono+NET Bridge version "+Util.VERSION);
	disclaimer();
	System.err.println("Usage: MonoBridge.exe [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("Example: MonoBridge.exe");
	System.err.println("Example: MonoBridge.exe INET_LOCAL:0 3 MonoBridge.log");
    }
    protected void javaUsage() {
	System.err.println("PHP/Java Bridge version "+Util.VERSION);
	disclaimer();
	System.err.println("Usage: java -jar JavaBridge.jar [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("SOCKETNAME is one of LOCAL, INET_LOCAL, INET, SERVLET_LOCAL, SERVLET");
	System.err.println("Example: java -jar JavaBridge.jar");
	System.err.println("Example: LD_LIBRARY_PATH=/usr/lib/php/modules/ java -jar JavaBridge.jar LOCAL:/tmp/javabridge_native.socket 3 /var/log/php-java-bridge.log");
	System.err.println("Example: java -jar JavaBridge.jar SERVLET_LOCAL:8080 3 JavaBridge.log");
	System.err.println("Influential system properties: threads, daemon, php_exec, default_log_file, default_log_level, base.");
	System.err.println("Example: java -Djava.awt.headless=\"true\" -Dphp.java.bridge.threads=50 -Dphp.java.bridge.base=/usr/lib/php/modules -Dphp.java.bridge.php_exec=/usr/local/bin/php-cgi -Dphp.java.bridge.default_log_file= -Dphp.java.bridge.default_log_level=5 -jar JavaBridge.jar");
	System.err.println("Example: java -Dphp.java.bridge.daemon=\"true\" -jar JavaBridge.jar");
    }
    protected void usage() {
	if(Util.IS_MONO)
	    monoUsage();
	else
	    javaUsage();
	
	System.exit(1);
    }

    protected void checkOption(String s[]) {
	if ("--version".equals(s[0])) {
	    System.out.println(Util.VERSION);
	    System.exit(0);
	}
	usage();
    }
    private static boolean testPort(int port) {
	try {
	    ServerSocket sock = new ServerSocket(port);
	    sock.close();
	    return true;
	} catch (IOException e) {/*ignore*/}
	return false;
    }
    private static int findFreePort(int start) {
	for (int port = start; port < start+100; port++) {
	    if(testPort(port)) return port;
	}
	return start;
   }

    /**
     * Copy of Util.IS_MONO. This is here because Util must not be accessed during startup.
     * @return true if this is the Mono VM, false otherwise
     */
    static boolean checkMono () {
        boolean IS_MONO = false;
        try {
            Util.CLRAssembly = Class.forName("cli.System.Reflection.Assembly");
            Util.loadFileMethod = Util.CLRAssembly.getMethod("LoadFile", new Class[] {String.class});
            Util.loadMethod = Util.CLRAssembly.getMethod("Load", new Class[] {String.class});
            IS_MONO=true;
        } catch (Exception e) {/*ignore*/}
        return IS_MONO;
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
	boolean isMono = checkMono();
        String tcpSocketName = isMono ? "9167" : "9267";
	
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
	    if(s.length==0 && !isMono) {
		try {
		    int tcpSocket = Integer.parseInt(tcpSocketName);
		    int freeJavaPort = findFreePort(tcpSocket);
		    int freeHttpPort = findFreePort(Standalone.HTTP_PORT_BASE);
		    Object result = JOptionPane. showInputDialog(null,
			    "Start a socket listener on port", "Starting the PHP/Java Bridge ...", JOptionPane.QUESTION_MESSAGE, null,
		            new String[] {"LOCAL:/var/run/.php-java-bridge_socket", 
			    "INET_LOCAL:"+freeJavaPort,"INET:"+freeJavaPort,
			    "SERVLET_LOCAL:"+freeHttpPort,"SERVLET:"+freeHttpPort}, "SERVLET_LOCAL:"+freeHttpPort);
		       if(result==null) System.exit(0);
		      sockname  = result.toString();
		} catch (Throwable t) {/*ignore*/}
	    }

	    if(s.length==0) {
		// do not access Util unless invoked as standalone component
		TCPServerSocket.TCP_PORT_BASE=Integer.parseInt(tcpSocketName);
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

    /**
     * Returns the canonical windows file. For example c:\program files instead of c:\programme
     * @param path The path, may be an empty string.
     * @return the canonical file.
     */
    public static File getCanonicalWindowsFile (String path) {
            try {
                return new File(path).getCanonicalFile();
        } catch (IOException e) {
                return new File(path);
        }
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
    /* Don't use Util or DynamicJavaBridgeClassLoader at this stage! */
    private static final boolean checkGNUVM() {
	try {
	    return "libgcj".equals(System.getProperty("gnu.classpath.vm.shortname"));
	} catch (Throwable t) {
	    return false;
	}
    }
    private static String escape(String s) {
	return (s.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("$", "\\$"));
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
	// check for -Dphp.java.bridge.daemon=true
	if (!(System.getProperty("php.java.bridge.daemon", "false").equals("false"))) {
	    final String[] args = new String[s.length + 7];
	    args[0] = System.getProperty("php.java.bridge.daemon");
	    if ("true".equals(args[0])) args[0]="java";
	    args[1]="-Djava.library.path="+System.getProperty("java.library.path", ".");
	    args[2] = "-Djava.ext.dirs="+System.getProperty("java.ext.dirs", ".");
	    args[3] = "-Djava.awt.headless="+System.getProperty("java.awt.headless", "true");
	    args[4]="-classpath"; 
	    args[5]=System.getProperty("java.class.path", ".");
	    args[6] = "php.java.bridge.Standalone";

	    for (int j=0; j<s.length; j++) {
		args[j+7]=s[j];
	    }
	    new Util.Thread(new Runnable () {
		public void run() {
		    try {
			if(false && System.getProperty("php.java.bridge.daemon_debug", "false").equals("true")) { 
			    try { System.in.close(); } catch (IOException e) {e.printStackTrace();}
			    System.out.close();
			    System.err.close();
			}  
			Runtime.getRuntime().exec(args);
		    } catch (IOException e) {
			e.printStackTrace();
		    }
		}
	    }).start();
	    try {Thread.sleep(20000);} catch (Throwable t) {}
	    System.exit (0);
	}
	
	try {
	    System.loadLibrary("natcJavaBridge");
	} catch (Throwable t) {/*ignore*/}
	try { // this hack tries to workaround two problems
	      // 1. On Unix an older JDK may be in the path, even though sun jdk >= 1.6 is installed
	      // 2. The standard Unix desktop ("Gnome") executes JavaBridge.jar from the $HOME dir, not the current dir
	    String cp = System.getProperty("java.class.path", ".");
	    File jbFile = null;
	    boolean isExecutableJavaBridgeJar = (cp.indexOf(File.pathSeparatorChar)==-1) && 
	    					cp.endsWith("JavaBridge.jar") && 
	    					((jbFile=new File(cp)).isAbsolute());
	    File wd = Standalone.getCanonicalWindowsFile(isExecutableJavaBridgeJar ? jbFile.getParent() : "");
	    boolean sunJavaInstalled = (new File("/usr/java/default/bin/java")).exists();
	    String javaExec = sunJavaInstalled ? "/usr/java/default/bin/java" : "java";

	    if(s.length==0 && 
		    (System.getProperty("php.java.bridge.exec_sun_vm", "true").equals("true")) &&
		    ((sunJavaInstalled && checkGNUVM()) || isExecutableJavaBridgeJar)) {
		Process p = Runtime.getRuntime().exec(new String[] {javaExec, "-Dphp.java.bridge.exec_sun_vm=false", "-classpath", cp, "php.java.bridge.Standalone"}, null, wd);
		if(p != null) System.exit(p.waitFor());
	    }
	} catch (Throwable t) {/*ignore*/}
	try {
	    (new Standalone()).init(s);
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(9);
	}
    }
}
