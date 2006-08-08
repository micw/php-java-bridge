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
public class ServletContextFactory extends php.java.bridge.http.SimpleContextFactory {
    protected HttpServletRequest proxy, req;
    protected HttpServletResponse res;
    protected ServletContext kontext;

    protected ServletContextFactory(ServletContext ctx, HttpServletRequest proxy, HttpServletRequest req, HttpServletResponse res) {
    	super(ctx.getRealPath(""));
    	this.kontext = ctx;
    	this.proxy = proxy;
    	this.req = req;
    	this.res = res;
    }
    
    /**
     * Set the HttpServletRequest for session sharing. 
     *
     * @param req The HttpServletRequest
     */
    public void setSession(HttpServletRequest req) {
    	this.proxy = req;
    }
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
	 // if name != null return a "named" php session which is not shared with jsp
	if(name!=null) return super.getSession(name, clientIsNew, timeout);
	
    	if(proxy==null) throw new NullPointerException("This context "+getId()+" doesn't have a session proxy.");
	return new HttpSessionFacade(kontext, proxy, res, timeout);
    }
    
    /**
     * Create and add a new ContextFactory.
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @return The created ContextFactory
     */
    public static ServletContextFactory addNew(ServletContext kontext, HttpServletRequest proxy, HttpServletRequest req, HttpServletResponse res) {
        ServletContextFactory ctx = new ServletContextFactory(kontext, proxy, req, res);
    	return ctx;
    }	

    public synchronized void destroy() {
    	super.destroy();
    	proxy=null;
    }
    public String toString() {
	return super.toString() + " has proxy: " +(proxy==null?"false":"true");
    }
    /**
     * Return an emulated JSR223 context.
     * @return The context.
     * @see php.java.faces.PhpFacesScriptContextFactory#getContext()
     * @see php.java.servlet.Context
     */
    public Object createContext() {
	return new Context(kontext, req, res);
    }
}
