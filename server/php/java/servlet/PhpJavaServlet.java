/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;

/**
 * Handles requests from PHP clients.  <p> When Apache, IIS or php
 * (cli) or php-cgi is used as a front-end, this servlet handles PUT
 * requests and then re-directs to a private (socket- or pipe-)
 * communication channel.  This is the fastest mechanism to connect
 * php and java. It is even 1.5 times faster than local ("unix
 * domain") sockets used by the php.java.bridge.JavaBridge standalone
 * listener.  </p>
 * <p>
 * To enable fcg/servlet debug code start the servlet engine with -Dphp.java.bridge.default_log_level=6.
 * For example: <code>java -Dphp.java.bridge.default_log_level=6 -jar /opt/jakarta-tomcat-5.5.9/bin/bootstrap.jar</code>
 * </p>
 * <p>There cannot be more than one PhpJavaServlet instance per web application. If you extend from this class, make sure to change
 * the .phpjavabridge =&gt; PhpJavaServlet mapping in the WEB-INF/web.xml. </p>
 */
public /*singleton*/ class PhpJavaServlet extends HttpServlet {

    private static final long serialVersionUID = 3257854259629144372L;

    private ContextServer contextServer;
    protected int logLevel = -1;
    
    private static Object illegalStateTest = new Object();
    
    /**
     * If you want to use the HTTP tunnel , set the
     * following flag to false and remove the &lt;distributable/&gt from
     * the WEB-INF/web.xml.
     * @see #handleHttpConnection(HttpServletRequest, HttpServletResponse, String)
     */
    public static final boolean IS_DISTRIBUTABLE = true;
    
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
	if (illegalStateTest == null) {
	    String msg = "FATAL ERROR: You're trying to recycle a destroyed servlet. Please unload this class!";
	    System.err.println (msg);
	    log (msg);
	    throw new ServletException (msg);
	}
	
 	String value;
        try {
	    value = config.getInitParameter("servlet_log_level");
	    if(value!=null && value.trim().length()!=0) logLevel=Integer.parseInt(value.trim());
        } catch (Throwable t) {Util.printStackTrace(t);}      
  	try {
	    value = config.getInitParameter("promiscuous");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) Util.JAVABRIDGE_PROMISCUOUS=true;
	} catch (Throwable t) {Util.printStackTrace(t);}      

	String servletContextName=CGIServlet.getRealPath(config.getServletContext(), "");
	if(servletContextName==null) servletContextName="";
	contextServer = new ContextServer(servletContextName);
    	 
    	super.init(config);
       
    	String logFile = null;
        try {
	    logFile = config.getInitParameter("servlet_log_file");
        } catch (Throwable t) {Util.printStackTrace(t);}      
    	if(!(Util.setConfiguredLogger(logFile)))
    		Util.setLogger(new Util.Logger(new Logger(config.getServletContext())));

	if(Util.VERSION!=null)
    	    Util.logMessage("PHP/Java Bridge servlet "+servletContextName+" version "+Util.VERSION+" ready.");
	else
	    Util.logMessage("PHP/Java Bridge servlet "+servletContextName+" ready.");
	
    }

    public void destroy() {
      	contextServer.destroy();
    	super.destroy();
    	illegalStateTest = null;
    }
    /**
     * This hook can be used to create a custom context factory. The default implementation checks if there's a ContextFactory 
     * by calling ContextFactory.get(req.getHeader("X_JAVABRIDGE_CONTEXT"), credentials); 
     * If it doesn't exist, a new RemoteServletContextFactory is created.
     * This procedure should set the response header X_JAVABRIDGE_CONTEXT as a side effect.
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @param credentials The provided credentials.
     * @return The (new) ServletContextFactory.
     */
    protected SimpleServletContextFactory getContextFactory(HttpServletRequest req, HttpServletResponse res, ContextFactory.ICredentials credentials) {
    	JavaBridge bridge;
	SimpleServletContextFactory ctx = null;
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	if(id!=null) ctx = (SimpleServletContextFactory) ContextFactory.get(id, credentials);
    	if(ctx==null) {
    	  ctx = RemoteServletContextFactory.addNew(this, getServletContext(), null, req, res); // no session sharing
    	  bridge = ctx.getBridge();
    	  bridge.logDebug("first request (session is new).");
    	} else {
    	    bridge = ctx.getBridge();
    	    bridge.logDebug("cont. session");
    	}
    	updateRequestLogLevel(bridge);
    	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    /**
     * Set the log level from the servlet into the bridge
     * @param bridgeThe JavaBridge from the ContextFactory.
     */
    protected void updateRequestLogLevel(JavaBridge bridge) {
	if(logLevel>-1) bridge.logLevel = logLevel;
    }

    /**
     * <p>
     * This hook can be used to suspend the termination of the servlet until the (Remote-)ServletContextFactory is finished.
     * It may be useful if one wants to access the Servlet, ServletContext or ServletRequest from a remote PHP script.
     * The notification comes from the php script when it is running as a sub component of the J2EE server or servlet engine.
     * </p>
     * <p>The default is to not wait for a local ServletContextFactory (the ContextFactory is passed from the PhpCGIServlet) 
     * and to wait RemoteContextFactory for 30 seconds.</p>
     * @param ctx The (Remote-) ContextFactory.
     */
    protected void waitForContext(SimpleServletContextFactory ctx) {
	try {
	    ctx.waitFor(Util.MAX_WAIT);
        } catch (InterruptedException e) {
	    Util.printStackTrace(e);
        }
    }

    /** Return the IS_DISTRIBUTABLE flag 
     * 
     * @return {@link #IS_DISTRIBUTABLE}
     */
    protected boolean getDistributable() {
	return IS_DISTRIBUTABLE;
    }
    /**
     * Handle a standard HTTP tunnel connection. Used when the local
     * channel is not available (security restrictions). Used by
     * Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     * @deprecated 
     */
    protected void handleHttpConnection (HttpServletRequest req, HttpServletResponse res, String channel)
	throws ServletException, IOException {

	/**
	 * The alternative would be to store the context in the session and to mark the context and all the classes it depends on
	 * (JavaBridge, ...) as java.io.Serializable.
	 */
	if(getDistributable()) Util.logMessage("HTTP tunnel not available in a distributable web application. " +
			"Either enable the Pipe- or SocketContextServer or remove the distributable flag from PhpJavaServlet and WEB-INF/web.xml.");

	InputStream in; ByteArrayOutputStream out;
	SimpleServletContextFactory ctx = getContextFactory(req, res, contextServer.getCredentials(channel));
	JavaBridge bridge = ctx.getBridge();
	ctx.setSessionFactory(req);

	if(req.getContentLength()==0) {
	    if(req.getHeader("Connection").equals("Close")) {
		bridge.logDebug("session closed.");
		ctx.destroy();
	    }
	    return;
	}
	bridge.in = in = req.getInputStream();
	bridge.out = out = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.init(in, out)) {
		r.handleRequests();
	    }
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
	res.setContentLength(out.size());
	out.writeTo(res.getOutputStream());
	in.close();
    }

    /**
     * Handle a redirected connection. The local channel is more than 50 
     * times faster than the HTTP tunnel. Used by Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void handleLocalConnection (HttpServletRequest req, HttpServletResponse res, String channel)
	throws ServletException, IOException {
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	SimpleServletContextFactory ctx = getContextFactory(req, res, null);
	JavaBridge bridge = ctx.getBridge();
	ctx.setSessionFactory(req);
	
	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	
	try {
	    if(r.init(sin, sout)) {
		AbstractChannelName channelName = contextServer.getFallbackChannelName(channel, ctx);
		res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());
	    	r.handleRequests();

		// redirect and re-open
		res.setContentLength(sout.size());
		resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("redirecting to port# "+ channelName);
	    	sin.close();
		try {res.flushBuffer(); } catch (Throwable t) {Util.printStackTrace(t);} // resin ignores resOut.close()
		try {resOut.close(); } catch (Throwable t) {Util.printStackTrace(t);} // Sun Java System AS 9 ignores flushBuffer()
		contextServer.start(channelName);
		this.waitForContext(ctx);
	    }
	    else {
	        ctx.destroy();
	    }
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }

    private boolean isLocal(HttpServletRequest req) {
        return req.getRemoteAddr().startsWith("127.0.0.1");
    }
    
    private String getHeader(String key, HttpServletRequest req) {
  	String val = req.getHeader(key);
  	if(val==null) return null;
  	if(val.length()==0) val=null;
  	return val;
    }
    /**
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
    	boolean local = Util.JAVABRIDGE_PROMISCUOUS || isLocal(req);
    	if(!local) throw new SecurityException("Non-local clients not allowed per default. " +
    			"Either \na) set promiscuous in your web.xml or \nb) start the Java VM with -Dphp.java.bridge.promiscuous=true " +
    			"to enable the SocketContextServer for non-local clients.");
    	String channel = getHeader("X_JAVABRIDGE_CHANNEL", req);

    	if(local && contextServer.isAvailable(channel)) 
    	    handleLocalConnection(req, res, channel); /* re-direct */
    	else
    	    handleHttpConnection(req, res, channel); /* standard http tunnel */
    }
    /** For backward compatibility */
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
      		String uri = req.getRequestURI();
     		req.getRequestDispatcher(uri.substring(0, uri.length()-10)).forward(req, res);
    }
}
