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


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.ISession;
import php.java.bridge.JavaBridgeFactory;
import php.java.bridge.Request;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.bridge.http.IContextFactoryVisitor;

/**
 * Create session contexts for servlets.<p> 
 * This ContextFactory can be used in environments where no custom class loaders and no threads are allowed.
 *
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.bridge.http.ContextServer
 * @author jostb
 */
public class RemoteHttpServletContextFactory extends JavaBridgeFactory implements IContextFactory, Serializable {

    private static final HashMap contexts = new HashMap();
    
    public static final String CONTEXT_FACTORY_ATTRIBUTE = RemoteHttpServletContextFactory.class.getName()+".ROOT";
    
    private static final long serialVersionUID = -7009009517347609467L;

    /** The PhpCGIServlet */
    protected Servlet servlet;
    /** The ServletContext of the PhpCGIServlet */
    protected ServletContext kontext;
    /** The session proxy (HttpServletRequest) of the PhpCGIServlet or the PhpJavaServlet */
    protected HttpServletRequest proxy;
    /** The PhpCGIServlet request */
    protected HttpServletRequest  req;
    /** The PhpCGIServlet response */
    protected HttpServletResponse res; 
    /** The PhpJavaServlet response */
    protected HttpServletResponse out;
    

    private IContext context;
    private ISession session;
    private IContextFactoryVisitor impl;
    private String id;
    
    private static long counter = 0;
    private static synchronized String addNext(RemoteHttpServletContextFactory thiz, String webContext) {
        String id;
        counter++;
        id = Long.toHexString(counter)+"@"+webContext;
        contexts.put(id, thiz);
        return id;
    }

    protected RemoteHttpServletContextFactory(Servlet servlet,
            ServletContext ctx, HttpServletRequest proxy,
            HttpServletRequest req, HttpServletResponse res) {
    	super();
    	this.kontext = ctx;
    	this.proxy = proxy;
    	this.req = req;
    	this.res = res;
    	this.servlet = servlet;
    	
    	this.id = addNext(this, ServletUtil.getRealPath(ctx, ""));
    }
    protected void accept (IContextFactoryVisitor impl) {
	this.impl = impl;
	impl.visit(this);
    }
    
    /**
     * Only for internal use.
     *  
     * Returns the context factory associated with the given <code>id</code>
     * @param id The ID
     */
    public static synchronized IContextFactory get(String id) {
	return (IContextFactory) contexts.get(id);
    }
    
    private static synchronized IContextFactory remove(String id) {
	return (IContextFactory) contexts.remove(id);
    }
    
    /**
     * Create and add a new ContextFactory.
     * @param servlet The servlet
     * @param kontext The servlet context
     * @param proxy The request proxy
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @return The created ContextFactory
     */
    public static IContextFactory addNew(Servlet servlet,
            ServletContext kontext, HttpServletRequest proxy,
            HttpServletRequest req, HttpServletResponse res, IContextFactoryVisitor impl) {
	RemoteHttpServletContextFactory factory = new RemoteHttpServletContextFactory(servlet, kontext, proxy, req, res);
	factory.accept(impl);
	return factory;
    }
    /**{@inheritDoc}*/
    public String getId() {
	return id;
    }
    /**{@inheritDoc}*/
    public String getRedirectString() {
	if (impl != null) return impl.getRedirectString();
	throw new IllegalStateException("getRedirectString");
    }
    /**{@inheritDoc}*/
    public String getRedirectString(String webPath) {
	if (impl != null) return impl.getRedirectString(webPath);
	throw new IllegalStateException("getRedirectString");
    }
   /**{@inheritDoc}*/
    public String getSocketName() {
	if (impl != null) return impl.getSocketName();
	throw new IllegalStateException("getSocketName");
    }
    /**{@inheritDoc}*/
    public ISession getSimpleSession(String name, boolean clientIsNew,
            int timeout) {
	throw new IllegalStateException("Named sessions not supported by servlet.");
    }
    /**{@inheritDoc}*/
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
	 // if name != null return a "named" php session which is not shared with jsp
	if(name!=null) return getSimpleSession(name, clientIsNew, timeout);

	if(session != null) return session;

   	if(proxy==null) throw new NullPointerException("This context "+getId()+" doesn't have a session proxy.");
	return session = new HttpSessionFacade(this, kontext, proxy, res, clientIsNew, timeout);
   }

    /**
     * Return the http session handle;
     * @throws IllegalStateException if java_session has not been called at the beginning of the PHP script
     * @return The session handle
     */
    HttpSession getCurrentSession() {
	if(session!=null) return ((HttpSessionFacade)session).getCachedSession();
	SimpleServletContextFactory.throwJavaSessionException();
	return null;
    }

    /**{@inheritDoc}*/
    public void initialize() {
	throw new IllegalStateException("initialize");
    }

    /**{@inheritDoc}*/
    public void invalidate() {
	throw new IllegalStateException("invalidate");
    }

    /**{@inheritDoc}*/
    public void recycle(String id) {
	throw new IllegalStateException("recycle");
    }

    /**{@inheritDoc}*/
    public void release() {
	if (impl != null) impl.release();
	else throw new IllegalStateException("release");
    }

    /**{@inheritDoc}*/
    public void releaseManaged() throws InterruptedException {
	if (impl != null) impl.releaseManaged();
	else throw new IllegalStateException("releaseManaged");

    }

    /**{@inheritDoc}*/
    public void waitFor(long timeout) throws InterruptedException {}

    /**
     * {@inheritDoc}
     */
    public void parseHeader(Request req, InputStream in) throws IOException {
	throw new IllegalStateException("parseHeader");
    }

    /**{@inheritDoc}*/
    public void setContext(IContext context) {
	if (impl != null) impl.setContext(context);
	this.context = context;
    }

    /**{@inheritDoc}*/
    public IContext getContext() {
	if (context != null) return context;
	if(impl != null) context = impl.getContext();
	else setContext(createContext());
	
	context.setAttribute(IContext.JAVA_BRIDGE, getBridge(), IContext.ENGINE_SCOPE);

        return context;
    }

    /**{@inheritDoc}*/
   public void destroy() {
	super.destroy();
	remove(getId());
   }

    /**
     * Return an emulated JSR223 context.
     * @return The context.
     * @see php.java.servlet.Context
     */
    private IContext createContext() {
	
	IContext ctx = new Context(kontext, req, res);
	ctx.setAttribute(IContext.SERVLET_CONTEXT, kontext, IContext.ENGINE_SCOPE);
	ctx.setAttribute(IContext.SERVLET_CONFIG, servlet.getServletConfig(), IContext.ENGINE_SCOPE);
	ctx.setAttribute(IContext.SERVLET, servlet, IContext.ENGINE_SCOPE);

	ctx.setAttribute(IContext.SERVLET_REQUEST, new HttpServletRequestWrapper(req) {
	    /*
	     * Return the session obtained from the servlet.
	     */
	    public HttpSession getSession() {
		return RemoteHttpServletContextFactory.this.getCurrentSession();
	    }

	    /*
	     * Return the old session or give up.
	     */
	    public HttpSession getSession(boolean clientIsNew) {
		HttpSession session = getSession();
		if(clientIsNew && !session.isNew())
		    throw new IllegalStateException("To obtain a new session call java_session() or \"java_session(null, -1, TRUE)\" at the beginning of your PHP script.");
		return session;
	    }
	    
	}, IContext.ENGINE_SCOPE);
		
	ctx.setAttribute(IContext.SERVLET_RESPONSE, new RemoteHttpServletResponse(res), IContext.ENGINE_SCOPE);
	
	return ctx;
    }

    /**{@inheritDoc}*/
   public void flushBuffer() throws IOException {
       out.flushBuffer();
   }

   /**
    * Set the current response 
    * @param out the PhpJavaServlet response
    */
   public void setResponse(HttpServletResponse out) {
       this.out = out;
   }
}
