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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HttpRequest;
import php.java.bridge.http.HttpResponse;
import php.java.bridge.http.HttpServer;
import php.java.bridge.http.IContextFactory;

/**
 * This is the main entry point for the PHP/Java Bridge library.
 * Example:<br>
 * public MyClass { <br>
 * &nbsp;&nbsp;public static void main(String s[]) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; JavaBridgeRunner runner = JavaBridgeRunner.getInstance();<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; // connect to port 9267 and send protocol requests ... <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;runner.destroy();<br>
 * &nbsp;&nbsp;}<br>
 * }<br>
 * @author jostb
 * @see php.java.script.PhpScriptContext
 */
public class JavaBridgeRunner extends HttpServer {

    private static String serverPort;
    private boolean isStandalone = false;
    /**
     * Create a new JavaBridgeRunner and ContextServer.
     * @throws IOException 
     * @see ContextServer
     */
    private JavaBridgeRunner() throws IOException {
	super();
	ctxServer = new ContextServer(ContextFactory.EMPTY_CONTEXT_NAME);
    }
    private static JavaBridgeRunner runner;
    /**
     * Return a instance.
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getInstance() throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner();
	return runner;
    }
    
    /**
     * Return a standalone instance. 
     * It sets a flag which indicates that the runner will be used as a standalone component outside of the Servlet environment.
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getStandaloneInstance() throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner();
	runner.isStandalone = true;
	return runner;
    }
    private static ContextServer ctxServer = null;

    /**
     * Create a server socket.
     * @param addr The host address, either INET:port or INET_LOCAL:port
     * @return The server socket.
     */
    public ISocketFactory bind(String addr) throws IOException {
	if(serverPort!=null) addr = (Util.JAVABRIDGE_PROMISCUOUS ? "INET:" :"INET_LOCAL:") +serverPort;  
	socket =  JavaBridge.bind(addr);
	return socket;
    }

    private static IContextFactory getContextFactory(HttpRequest req, HttpResponse res, ContextFactory.ICredentials credentials) {
    	String id = getHeader("X_JAVABRIDGE_CONTEXT", req);
    	IContextFactory ctx = ContextFactory.get(id, credentials);
	if(ctx==null) ctx = ContextFactory.addNew();
     	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    private static String getHeader(String key, HttpRequest req) {
  	String val = req.getHeader(key);
  	if(val==null) return null;
  	if(val.length()==0) val=null;
  	return val;
    }

    /**
     * Handles both, override-redirect and redirect, see
     * @see php.java.servlet.PhpJavaServlet#handleSocketConnection(HttpServletRequest, HttpServletResponse, String, boolean)
     * @see php.java.servlet.PhpJavaServlet#handleRedirectConnection(HttpServletRequest, HttpServletResponse)
     * 
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void doPut (HttpRequest req, HttpResponse res) throws IOException {
    	boolean override_redirect="1".equals(getHeader("X_JAVABRIDGE_REDIRECT", req));
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
    	String channel = getHeader("X_JAVABRIDGE_CHANNEL", req);
	String kontext = getHeader("X_JAVABRIDGE_CONTEXT_DEFAULT", req);
	ContextFactory.ICredentials credentials = ctxServer.getCredentials(channel, kontext);
	IContextFactory ctx = getContextFactory(req, res, credentials);

    	JavaBridge bridge = ctx.getBridge();
	// save old state for override_redirect
	InputStream bin = bridge.in;
	OutputStream bout = bridge.out;
	Request br  = bridge.request;

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
        if(r.init(sin, sout)) {
        	AbstractChannelName channelName = 
                    ctxServer.getFallbackChannelName(channel, kontext, ctx);
        	boolean hasDefault = ctxServer.schedule(channelName) != null;
        	res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getDefaultName());
        	if(hasDefault) res.setHeader("X_JAVABRIDGE_CONTEXT_DEFAULT", kontext);
        	r.handleRequests();
        	ctxServer.recycle(channelName);
        
        	// redirect and re-open
        	if(override_redirect) {
        	    bridge.logDebug("restore state");
        	    bridge.in = bin; bridge.out = bout; bridge.request = br; 
        	} else {
        	    if(bridge.logLevel>3) 
        		bridge.logDebug("re-directing to port# "+ channelName);
        	}
        	res.setContentLength(sout.size());
        	resOut = res.getOutputStream();
        	sout.writeTo(resOut);
            resOut.close();
            if(!override_redirect) {
                ctxServer.start(channelName);
        	    if(bridge.logLevel>3) 
        		bridge.logDebug("waiting for context: " +ctx.getId());
        	    try { ctx.waitFor(); } catch (InterruptedException e) { Util.printStackTrace(e); }
        	}
        }
        else {
            ctx.destroy();
        }
    }
    /**
     * Handle doGet requests. For example java_require("http://localhost:8080/JavaBridge/java/Java.inc");
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void doGet (HttpRequest req, HttpResponse res) throws IOException {
	String name =req.getRequestURI(); 
	if(name==null) { super.doGet(req, res); return; }
	name = name.replaceAll("/JavaBridge", "META-INF");
	InputStream in = JavaBridgeRunner.class.getClassLoader().getResourceAsStream(name);
	if(in==null) { res.setContentLength(ERROR.length); res.getOutputStream().write(ERROR); return; }
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	byte[] buf = new byte[Util.BUF_SIZE];
	int c;
	while((c=in.read(buf))>0) bout.write(buf, 0, c);
	res.setContentLength(bout.size());
	OutputStream out = res.getOutputStream();
	try {
	    bout.writeTo(out);
	} catch (IOException e) { /* may happen when the client is not interested, see require_once() */}
    }
    /**
     * Return true if this is a standalone server
     * @return true if this runner is a standalone runner (see {@link #main(String[])}) , false otherwise.
     */
    public boolean isStandalone() {
	return isStandalone;
    }
    /**
     * For internal tests only.
     * @throws InterruptedException 
     * @throws IOException 
     */
    public static void main(String s[]) throws InterruptedException, IOException {
	 if(s!=null) {
	     if(s.length>0 && s[0]!=null) serverPort = s[0];
	 }
	 Util.logMessage("JavaBridgeRunner started on port " + serverPort);
	 JavaBridgeRunner r = getStandaloneInstance();
	 r.httpServer.join();
	 r.destroy();
    }
}
