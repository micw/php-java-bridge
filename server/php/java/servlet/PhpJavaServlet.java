/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.Session;
import php.java.bridge.Util;


public class PhpJavaServlet extends HttpServlet {
    public void init(ServletConfig config) throws ServletException {
	// FIXME: parse config
	String s[]= new String[]{"9167", "5", ""};
	String sockname, logFile="";
	try {
	    if(s.length>0) {
		sockname=s[0];
	    } 
	    try {
		if(s.length>1) {
		    Util.logLevel=Integer.parseInt(s[1]);
		} else {
		    Util.logLevel=Util.DEFAULT_LOG_LEVEL;
		}
	    } catch (Throwable t) {
		Util.printStackTrace(t);
	    }

	    try {
		logFile="";
		if(s.length>2) {
		    logFile=s[2];
		    if(Util.logLevel>3) System.out.println("Java log         : " + logFile);
		}
	    }catch (Throwable t) {
		Util.printStackTrace(t);
	    }
	    boolean redirectOutput = false;
	    try {
		redirectOutput = JavaBridge.openLog(logFile);
	    } catch (Throwable t) {
	    }
			    
	    if(!redirectOutput) {
		try {
		    Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
		} catch (Exception e) {
		    Util.logStream=System.out;
		}
	    } else {
		Util.logStream=System.out;
		logFile="<stdout>";
	    }
	} catch (Throwable t) {t.printStackTrace();}
		
    }

    public void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
	HttpSession session = req.getSession();
	JavaBridge bridge = (JavaBridge) session.getAttribute("bridge");
	if(bridge==null) {
	    bridge = new JavaBridge();
	    session.setAttribute("bridge", bridge);
	}
	in=bridge.in=req.getInputStream();
	bridge.out=out=new ByteArrayOutputStream();
	Request r = new Request(bridge);
	try {
	    if(r.initOptions(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	Session.expire();
        Util.logDebug(this + " " + "request terminated.");
        res.setContentLength(out.size());
        out.writeTo(res.getOutputStream());
    }
}
