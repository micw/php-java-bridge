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


/**
 * This is the native standalone container of the PHP/Java Bridge. It starts
 * the standalone back-end, listenes for protocol requests and handles
 * CreateInstance, GetSetProp and Invoke requests. Supported protocol
 * modes are INET (listens on all interfaces), INET_LOCAL (loopback
 * only), LOCAL, SERVLET and SERVLET_LOCAL 
 * (starts the built-in servlet engine listening on all interfaces or loopback).  
 * <p> Example:<br> <code> java
 * INET_LOCAL:9676 5
 * bridge.log &amp;<br> telnet localhost 9676<br> &lt;CreateInstance
 * value="java.lang.Long" predicate="Instance" id="0"&gt;<br> &lt;Long
 * value="6"/&gt; <br> &lt;/CreateInstance&gt;<br> &lt;Invoke
 * value="1" method="toString" predicate="Invoke" id="0"/&gt;<br>
 * </code>
 *
 */

public class StandaloneGCC extends Standalone {

    protected void checkOption(String s[]) {
	if ("--version".equals(s[0])) {
	    System.out.println("PHP/Java Bridge GCC/GCJ");
	    System.exit(0);
	}
	usage();
    }
    protected void javaUsage() {
	System.err.println("PHP/Java Bridge GCC/GCJ");
	disclaimer();
	System.err.println("Usage: java [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("SOCKETNAME is one of LOCAL, INET_LOCAL, INET, SERVLET_LOCAL, SERVLET");
	System.err.println("Example: java");
	System.err.println("Example: java LOCAL:/tmp/javabridge_native.socket 3 /var/log/php-java-bridge.log");
	System.err.println("Example: java INET:9267 3 JavaBridge.log");
	System.err.println("Example: java SERVLET:8080 3 JavaBridge.log");
    }

    /**
     * Start the PHP/Java Bridge. <br>
     * Example:<br>
     * <code>java LOCAL:@socket 5 /var/log/php-java-bridge.log</code><br>
     * Note: Do not write anything to System.out, this
     * stream is connected with a pipe which waits for the channel name.
     * @param s an array of [socketname, level, logFile]
     */
    public static void main(String s[]) {
	java.util.LinkedList list = new java.util.LinkedList();
	for (int i=0; i<s.length; i++) {
	    boolean consumed = false;
	    if (s[i].startsWith("-D")) {
		String str = s[i].substring(2);
		int p=str.indexOf('=');
		if (p!=-1) {
		    String key = str.substring(0, p).trim();
		    String val = str.substring(p+1).trim();
		    System.setProperty(key, val);
		    consumed = true;
		}
	    }
	    if (!consumed) list.add(s[i]);
	}

	String[] args = (String[])list.toArray(new String[0]);

	// check for -Dphp.java.bridge.daemon=true
	if (!(System.getProperty("php.java.bridge.daemon", "false").equals("false"))) {
	    java.util.LinkedList list2 = new java.util.LinkedList();

	    list2.add(System.getProperty("gnu.gcj.progname", "java"));
	    list2.add("-Djava.awt.headless="+System.getProperty("java.awt.headless", "true"));
	    list2.add("-Dphp.java.bridge.asDaemon=true");
	    for (int i=0; i<args.length; i++) {
		if(args[i].startsWith("-D") && args[i].substring(2).trim().startsWith("php.java.bridge.daemon"))
		   continue;

		list2.add(args[i]);
	    }

	    final String[] args2 = (String[])list2.toArray(new String[0]);

	    try {
	        System.in.close();
	        System.out.close();
		System.err.close();
	    } catch (java.io.IOException e) {
		System.exit(12);
	    }
	    new Util.Thread(new Runnable () {
		public void run() {
		    try {
			Runtime.getRuntime().exec(args2);
		    } catch (java.io.IOException e) {
			System.exit(13);
		    }
		}
	    }).start();
	    try {Thread.sleep(20000);} catch (Throwable t) {}
	    System.exit (0);
	}

	try {
	    System.loadLibrary("natcJavaBridge");
	} catch (Throwable t) {/*ignore*/}

	(new StandaloneGCC()).init(args);
    }
}
