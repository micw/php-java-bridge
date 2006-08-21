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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HttpRequest;
import php.java.bridge.http.HttpResponse;
import php.java.bridge.http.HttpServer;
import php.java.bridge.http.IContextFactory;
import php.java.bridge.http.IContextServer;

/**
 * This is the main entry point for the PHP/Java Bridge library.
 * Example:<br>
 * public MyClass { <br>
 * &nbsp;&nbsp;public static void main(String s[]) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; JavaBridgeRunner runner = new JavaBridgeRunner();<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; // connect to port 9267 and send protocol requests ... <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;runner.destroy();<br>
 * &nbsp;&nbsp;}<br>
 * }<br>
 * @author jostb
 * @see php.java.script.PhpScriptContext
 */
public class JavaBridgeRunner extends HttpServer {

    /**
     * Create a new JavaBridgeRunner and ContextServer.
     * @see ContextServer
     */
    public JavaBridgeRunner() {
	super();
	if(ctxServer!=null) throw new IllegalStateException("There can be only one JavaBridgeRunner per class loader");
	ctxServer = new ContextServer(new ThreadPool("JavaBridgeContextRunner", Integer.parseInt(Util.THREAD_POOL_MAX_SIZE)));
    }

    private static ContextServer ctxServer = null;

    /**
     * Create a server socket.
     */
    public ISocketFactory bind() {
	try {
            boolean promisc = (System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true"));
	    socket =  promisc ? JavaBridge.bind("INET:0") : JavaBridge.bind("INET_LOCAL:0");
	    if(Util.logLevel>3) Util.logDebug("JavaBridgeRunner started on port " + socket);
	    return socket;
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    return null;
	}
    }

    private static IContextFactory getContextFactory(HttpRequest req, HttpResponse res) {
    	String id = getHeader("X_JAVABRIDGE_CONTEXT", req);
    	IContextFactory ctx = ContextFactory.get(id, ctxServer);
	if(ctx==null) ctx = ContextFactory.addNew(ContextFactory.EMPTY_CONTEXT_NAME);
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
    protected void parseBody (HttpRequest req, HttpResponse res) throws IOException {
    	super.parseBody(req, res);
	boolean override_redirect="1".equals(getHeader("X_JAVABRIDGE_REDIRECT", req));
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	IContextFactory ctx = getContextFactory(req, res);

    	JavaBridge bridge = ctx.getBridge();
	// save old state for override_redirect
	InputStream bin = bridge.in;
	OutputStream bout = bridge.out;
	Request br  = bridge.request;

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);

	try {
	    if(r.init(sin, sout)) {
		String kontext = getHeader("X_JAVABRIDGE_CONTEXT_DEFAULT", req);
		IContextServer.ChannelName channelName = 
	            ctxServer.getFallbackChannelName(getHeader("X_JAVABRIDGE_CHANNEL", req), kontext, ctx);
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
	    	sin.close(); sin=null;
	    	resOut.close(); resOut=null;
	    	if(!override_redirect) {
		    ctxServer.start(channelName);
		    if(bridge.logLevel>3) 
			bridge.logDebug("waiting for context: " +ctx.getId());
		    ctx.waitFor();
		}
	    }
	    else {
	        sin.close(); sin=null;
	        ctx.destroy();
	    }
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    try {if(sin!=null) sin.close();} catch (IOException e1) {}
	    try {if(resOut!=null) resOut.close();} catch (IOException e2) {}
	}
    }
    
    /**
     * For internal tests only.
     * @throws InterruptedException 
     */
    public static void main(String s[]) throws InterruptedException {
	//System.setProperty("php.java.bridge.default_log_level", "4");
	//System.setProperty("php.java.bridge.default_log_file", "");
	JavaBridgeRunner r = new JavaBridgeRunner();
	r.httpServer.join();
	r.destroy();
    }
}
