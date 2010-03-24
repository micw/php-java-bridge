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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Util;

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

    protected int logLevel = -1;
    private Util.Logger logger;
    protected boolean promiscuous = false;

    // workaround for a bug in weblogic server, see below
    private boolean isWebLogic = false;
    // workaround for a bug in jboss server, which uses the log4j port 4445 for its internal purposes(!)
    private boolean isJBoss = false;
    
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
 	String servletContextName=ServletUtil.getRealPath(config.getServletContext(), "");
	if(servletContextName==null) servletContextName="";
	ServletContext ctx = config.getServletContext();

	String value = ctx.getInitParameter("promiscuous");
	if(value==null) value="";
	value = value.trim();
	value = value.toLowerCase();
	
	if(value.equals("on") || value.equals("true")) promiscuous=true;
    	 
    	super.init(config);
       
	String name = ctx.getServerInfo();
	if (name != null && (name.startsWith("WebLogic"))) isWebLogic = true;
	if (name != null && (name.startsWith("JBoss")))    isJBoss    = true;

	logger = new Util.Logger(!isJBoss, new Logger(ctx));
    	
	if(Util.VERSION!=null)
    	    log("PHP/Java Bridge servlet "+servletContextName+" version "+Util.VERSION+" ready.");
	else
	    log("PHP/Java Bridge servlet "+servletContextName+" ready.");
	
    }

    /**{@inheritDoc}*/
    public void destroy() {
	ServletContext ctx = getServletContext();
	try {
	    ContextLoaderListener.destroyCloseables(ctx);
	    ContextLoaderListener.destroyScriptEngines(ctx);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
	
    	super.destroy();
    	
    	Util.destroy();
    }
    /*    /**
     * Set the log level from the servlet into the bridge
     * @param bridge The JavaBridge from the ContextFactory.
     */
    protected void updateRequestLogLevel(JavaBridge bridge) {
	if(logLevel>-1) bridge.logLevel = logLevel;
    }
   
    /** Only for internal use */
    public static String getHeader(String key, HttpServletRequest req) {
  	String val = req.getHeader(key);
  	if(val==null) return null;
  	if(val.length()==0) val=null;
  	return val;
    }
    private InputStream getInputStream (HttpServletRequest req) throws IOException {
	InputStream in = req.getInputStream();
	if (!isWebLogic) return in;
	
	return new FilterInputStream(in) {
	    /**
	     * Stupid workaround for WebLogic's insane chunked reader implementation, it blocks instead of simply returning what's available so far.
	     * in.getAvailable() can't be used either, because it returns the bytes in weblogics internal cache: For 003\r\n123\r\n weblogic 10.3 returns
	     * 10 instead of 3(!) 
	     */
	    public int read(byte[] buf, int pos, int length) throws IOException {
		return in.read(buf, pos, 1);
	    }
	};
    }
    protected void handleHttpConnection (HttpServletRequest req, HttpServletResponse res) 
	throws ServletException, IOException {
	boolean destroyCtx = false;

	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
	RemoteHttpServletContextFactory ctx;
	if (id!=null) {
	    ctx = (RemoteHttpServletContextFactory) RemoteHttpServletContextFactory.get(id);
	    if (ctx==null) throw new IllegalStateException("Cannot find RemoteHttpServletContextFactory");
	} else {
	    ctx = new RemoteHttpServletContextFactory(this, getServletContext(), req, req, res);
	    destroyCtx = true;
	}
	
	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
	res.setHeader("Pragma", "no-cache");
	res.setHeader("Cache-Control", "no-cache");
	InputStream sin=null; OutputStream sout = null;
	JavaBridge bridge = ctx.getBridge();
    	
	bridge.in = sin = getInputStream (req);
	bridge.out = sout = res.getOutputStream();
	ctx.setResponse (res);
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.init(sin, sout)) {
		r.handleRequests();
	    }
	    else {
		Util.warn("handleHttpConnection init failed");
	    }
	} finally {
	    if (destroyCtx) ctx.destroy();
	}
    }

    protected void handlePut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	Util.setLogger(logger);

    	if(Util.logLevel>3) Util.logDebug("doPut:"+req.getRequestURL()); 

    	handleHttpConnection(req, res);
    }
    
    /**
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res) 
    	throws ServletException, IOException {
	try {
	    handlePut(req, res);
	} catch (RuntimeException e) {
	    Util.printStackTrace(e);
	    throw new ServletException(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw e;
	} catch (ServletException e) {
	    Util.printStackTrace(e);
	    throw e;
	}
    }
}
