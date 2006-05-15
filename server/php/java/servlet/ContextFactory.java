/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.ISession;

/**
 * Create session contexts for servlets.<p> In addition to the
 * standard ContextFactory this manager keeps a reference to the
 * HttpServletRequest.
 *
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.bridge.http.ContextServer
 */
public class ContextFactory extends php.java.bridge.http.ContextFactory {
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    protected ServletContext kontext;

    protected ContextFactory(ServletContext ctx, HttpServletRequest proxy, HttpServletRequest req, HttpServletResponse res) {
    	super();
    	this.kontext = ctx;
    	this.req = proxy;
    	this.res = res;
    }
    
    /**
     * Set the HttpServletRequest for session sharing. 
     *
     * @param req The HttpServletRequest
     *
     * @throws IllegalStateException When the ContextFactory was
     * created with a HttpServletRequest or when this method was
     * called twice.
     */
    public void setSession(HttpServletRequest req) {
      //FIXME
    	//if(this.proxy!=null) throw new IllegalStateException("This context already has a session proxy.");
    	this.req = req;
    }
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
    	if(req==null) throw new NullPointerException("This context "+getId()+" doesn't have a session proxy.");
	return new HttpSessionFacade(kontext, req, res, timeout);
    }
    
    /**
     * Create and add a new ContextFactory.
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @return The created ContextFactory
     */
    public static ContextFactory addNew(ServletContext kontext, HttpServletRequest proxy, HttpServletRequest req, HttpServletResponse res) {
    	ContextFactory ctx = new ContextFactory(kontext, proxy, req, res);
    	return ctx;
    }	

    public synchronized void destroy() {
    	super.destroy();
    	req=null;
    }
    public String toString() {
	return super.toString() + ", has proxy: " +(req==null?"false":"true");
    }
    
    /**
     * Return an emulated JSR223 context.
     * @return The context.
     * @see php.java.faces.PhpFacesScriptContextFactory#getContext()
     * @see php.java.servlet.Context
     */
    public Object getContext() {
	if(context==null) setContext(new Context(kontext, req, res));
	return context;
    }
    public void recycle(php.java.bridge.http.ContextFactory target) {
        super.recycle(target);
        // the persistent connection needs the fresh values
        ContextFactory fresh = (ContextFactory)target;
        kontext = fresh.kontext;
        req = fresh.req;
        res = fresh.res;
    }
}
