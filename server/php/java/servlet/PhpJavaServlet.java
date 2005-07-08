/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.Request;
import php.java.bridge.Session;
import php.java.bridge.Util;


public class PhpJavaServlet extends HttpServlet {
    public static class Logger extends Util.Logger {
	private ServletContext ctx;
	public Logger(ServletContext ctx) {
	    this.ctx = ctx;
	}
	public void log(String s) { ctx.log(s); }
	public String now() { return ""; }
	public void printStackTrace(Throwable t) {
	    ctx.log(Util.EXTENSION_NAME + " Exception: ", t);
	}
    }

    public void init(ServletConfig config) throws ServletException {
	Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	Util.logger=new Logger(config.getServletContext());
        DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);
    }

    public void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
        try {
	    HttpSession session = req.getSession();
	    JavaBridge bridge = (JavaBridge) session.getAttribute("bridge");
	    if(bridge==null) {
		bridge = new JavaBridge(null, null);
		bridge.cl = new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance());
		session.setAttribute("bridge", bridge);
	    }
	    if(req.getContentLength()==0) {
		if(req.getHeader("Connection").equals("Close")) {
		    session.invalidate();
		    Session.expire(bridge);
		    bridge.load--;
		    bridge.logDebug("session closed.");
		}
		return;
	    }
	    bridge.in = in = req.getInputStream();
	    bridge.out = out = new ByteArrayOutputStream();
	    if(session.isNew()) bridge.load++;
	    Request r = new Request(bridge);
	    try {
		if(r.initOptions(in, out)) {
		    r.handleRequests();
		}
	    } catch (Throwable e) {
		Util.printStackTrace(e);
	    }
	    if(session.isNew())
		bridge.logDebug("first request terminated (session is new).");
	    else
		bridge.logDebug("request terminated (cont. session).");
	    res.setContentLength(out.size());
	    out.writeTo(res.getOutputStream());
	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    res.getOutputStream().close();
	    req.getInputStream().close();
	}
    }
}
