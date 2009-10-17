/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

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

import java.net.URI;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.servlet.PhpJavaServlet;
import php.java.servlet.RemoteHttpServletContextFactory;

/**
 * A custom context factory, creates a ContextFactory for JSR223 contexts.
 * This context factory waits for the script continuation. Use the PhpServletContextFactory if
 * you don't want to call php methods from Java.
 * @see #initialize()
 * @see #releaseManaged() 
 * @see PhpServletContextFactory
 * @author jostb
 *
 */
public class InvocableRemotePhpServletContextFactory extends php.java.servlet.SimpleServletContextFactory {

    /** The official FIXED(!) IP# of the current host, */
    protected String localName;
    
    protected InvocableRemotePhpServletContextFactory(Servlet servlet, ServletContext ctx,
			HttpServletRequest proxy, HttpServletRequest req,
			HttpServletResponse res, String localName) {
	        super(servlet, ctx, proxy, req, res, true);
		this.localName = localName;
	}
    /**
     * Add the PhpScriptContext
     * @param context The passed context
     * @param servlet The servlet
     * @param kontext The servlet context
     * @param req The servlet request
     * @param res The servlet response
     * @param localName The official server name or IP# from the remote script engine's point of view. There must not be an IP based load balancer in between.
     * @return The ContextFactory.
     */
    public static IContextFactory addNew(ContextServer server, IContext context, Servlet servlet, 
		    ServletContext kontext, HttpServletRequest req, HttpServletResponse res, String localName) {
	IContextFactory ctx;
        if (server.isAvailable(PhpJavaServlet.getHeader("X_JAVABRIDGE_CHANNEL", req)))
	    ctx = new InvocableRemotePhpServletContextFactory(servlet, kontext, req, req, res, localName);
        else 
           ctx = RemoteHttpServletContextFactory.addNew(servlet, kontext, req, req, res, new InvocableRemotePhpServletContextFactory(servlet, kontext, req, req, res, localName));
            
        ctx.setContext(context);
        return ctx;
    }
    /**{@inheritDoc}*/
    public String getRedirectString(String webPath) {
        try {
            StringBuffer buf = new StringBuffer();
            buf.append(getSocketName());
            buf.append("/");
            buf.append(webPath);
            StringBuffer buf1 = new StringBuffer(req.isSecure()?"s:":"h:");
            buf1.append(localName);
            URI uri = new URI(buf1.toString(), buf.toString(), null);
            return (uri.toASCIIString()+".phpjavabridge");
        } catch (Exception e) {
            Util.printStackTrace(e);
        }
	StringBuffer buf = new StringBuffer();
	if(!req.isSecure())
		buf.append("h:");
	else
		buf.append("s:");
	buf.append(localName);
	buf.append(":");
	buf.append(getSocketName()); 
	buf.append('/');
	buf.append(webPath);
	buf.append(".phpjavabridge");
	return buf.toString();
    }
}
