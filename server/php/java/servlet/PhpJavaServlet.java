/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.ILogger;
import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.ThreadPool;
import php.java.bridge.Util;
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
 * @see php.java.bridge.JavaBridge
 *  */
public class PhpJavaServlet extends HttpServlet {

    private static final long serialVersionUID = 3257854259629144372L;

    private ContextServer contextServer;

    protected static class Logger implements ILogger {
	private ServletContext ctx;
	protected Logger(ServletContext ctx) {
	    this.ctx = ctx;
	}
	public void log(int level, String s) {
	    if(Util.logLevel>5) System.out.println(s);
	    ctx.log(s); 
	}
	public void printStackTrace(Throwable t) {
	    ctx.log("", t);
	    if(Util.logLevel>5) t.printStackTrace();
	}
	public void warn(String msg) {
	    ctx.log(msg);
	}
     }
    
    private boolean allowHttpTunnel = false;
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
 	String value;
        try {
	    value = config.getInitParameter("servlet_log_level");
	    //value = "6"; //XFIXME
	    if(value!=null && value.trim().length()!=0) Util.logLevel=Integer.parseInt(value.trim());
        } catch (Throwable t) {Util.printStackTrace(t);}      
  	try {
	    value = config.getInitParameter("allow_http_tunnel");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) allowHttpTunnel=true;
	} catch (Throwable t) {Util.printStackTrace(t);}      


    	contextServer = new ContextServer(new ThreadPool("JavaBridgeContextRunner", Integer.parseInt(Util.THREAD_POOL_MAX_SIZE)));
    	 
    	super.init(config);
       
	Util.setLogger(new Util.Logger(new Logger(config.getServletContext())));

	String servletContextName = servletContextName=config.getServletContext().getRealPath("");
	if(servletContextName==null) servletContextName="";
	if(Util.VERSION!=null)
    	    Util.logMessage("PHP/Java Bridge servlet "+servletContextName+" version "+Util.VERSION+" ready.");
	else
	    Util.logMessage("PHP/Java Bridge servlet "+servletContextName+" ready.");
	
    }

    public void destroy() {
      	contextServer.destroy();
    	super.destroy();
    }
    
    private ServletContextFactory getContextFactory(HttpServletRequest req, HttpServletResponse res) {
    	ServletContextFactory ctx = null;
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	if(id!=null) ctx = (ServletContextFactory) ContextFactory.get(id, contextServer);
    	if(ctx==null) {
    	  if(Util.logLevel>5) if(!id.equals("0")) throw new IllegalStateException("FATAL: Context #"+ id + " not found");
    	  ctx = ServletContextFactory.addNew(getServletContext(), null, req, res); // no session sharing
    	  ctx.getBridge().logDebug("first request (session is new).");
    	} else {
    	    ctx.getBridge().logDebug("cont. session");
    	}
    	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    
    /**
     * Handle the override re-direct for "java_get_session()" when php
     * runs within apache.  We
     * execute one statement and return the result and the allocated
     * session.  Used by Apache only.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void handleRedirectConnection(HttpServletRequest req, HttpServletResponse res) 
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out; OutputStream resOut;
	ServletContextFactory ctx = getContextFactory(req, res);
	JavaBridge bridge = ctx.getBridge();
	ctx.setSession(req);
	if(bridge.logLevel>3) bridge.logDebug("override redirect starts for " + ctx.getId());		
	// save old state
	InputStream bin = bridge.in;
	OutputStream bout = bridge.out;
	Request br  = bridge.request;
	
	bridge.in = in = req.getInputStream();
	bridge.out = out = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.init(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	// restore state
	bridge.in = bin; bridge.out = bout; bridge.request = br;
	
	res.setContentLength(out.size());
	resOut = res.getOutputStream();
	out.writeTo(resOut);
	in.close();
	if(bridge.logLevel>3) bridge.logDebug("override redirect finished for " + ctx.getId());
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
     */
    protected void handleHttpConnection (HttpServletRequest req, HttpServletResponse res, boolean session)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
	ServletContextFactory ctx = getContextFactory(req, res);
	JavaBridge bridge = ctx.getBridge();
	if(session) ctx.setSession(req);

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
	} catch (Throwable e) {
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
    protected void handleSocketConnection (HttpServletRequest req, HttpServletResponse res, String channel, boolean session)
	throws ServletException, IOException {
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	ServletContextFactory ctx = getContextFactory(req, res);
	JavaBridge bridge = ctx.getBridge();
	if(session) ctx.setSession(req);

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	
	try {
	    if(r.init(sin, sout)) {
	        ContextServer.ChannelName channelName = contextServer.getFallbackChannelName(channel);
	        res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());
	    	r.handleRequests();

		// redirect and re-open
	    	contextServer.schedule();
		res.setContentLength(sout.size());
		resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("re-directing to port# "+ channelName);
	    	sin.close(); sin=null;
		try {res.flushBuffer(); } catch (Throwable t) {Util.printStackTrace(t);}
		contextServer.start(channelName);
	    }
	    else {
	        sin.close(); sin=null;
	        ctx.destroy();
	    }
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    try {if(sin!=null) sin.close();} catch (IOException e1) {}
	}
    }

    private boolean isLocal(HttpServletRequest req) {
        return req.getRemoteAddr().startsWith("127.0.0.1");
    }
    
    private String getChannel(HttpServletRequest req) {
  	String channel = req.getHeader("X_JAVABRIDGE_CHANNEL");
  	if(channel==null) return null;
  	if(channel.length()==0) channel=null;
  	return channel;
    }
    /**
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
    	short redirect =(short) req.getIntHeader("X_JAVABRIDGE_REDIRECT");
    	boolean local = isLocal(req);
    	if(!local && !allowHttpTunnel) throw new SecurityException("Non-local clients not allowed per default. Set allow_http_tunnel in your web.xml.");
    	String channel = getChannel(req);
    	
    	try {
	    if(redirect==1) 
		handleRedirectConnection(req, res); /* override re-direct */
	    else if(local && contextServer.isAvailable(channel)) 
		handleSocketConnection(req, res, channel, redirect==2); /* re-direct */
	    else
		handleHttpConnection(req, res, redirect==2); /* standard http tunnel */

	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    try {req.getInputStream().close();} catch (IOException x2) {}
	}
    }
    /** For backward compatibility */
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
      		String uri = req.getRequestURI();
     		req.getRequestDispatcher(uri.substring(0, uri.length()-10)).forward(req, res);
    }
}
