/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.ISession;

/**
 * The context may a) keep a promise (a "proxy") which one may evaluate to a
 * session reference (for PHP/JSP session sharing), and/or b) it may
 * reference a "half-executed" bridge for local channel re-directs (for
 * "high speed" communication links).
 *
 * Context instances are kept in an array with no more than 65535
 * entries, after 65535 context creations the context is destroyed if
 * this did not happen explicitly.  A unique [1..65535] context
 * instance should be created for each request and destroyed when the request
 * is done.  

 * The string ID of the instance should be passed to the client, which may
 * pass it back together with the getSession request or the "local
 * channel re-direct". If the former happens, we invoke the promise
 * and return the session object to the client. Different promises can
 * evaluate to the same session object.  For local channel re-directs,
 * the Context is given to a ContextRunner, which handles the
 * local channel communication.
 * @see ContextRunner
 * @see ContextServer
 */
public class ContextManager extends php.java.bridge.ContextManager {
    private HttpServletRequest req;
    private HttpServletResponse res;
	
    protected ContextManager(HttpServletRequest req, HttpServletResponse res) {
    	super();
	this.req = req;
    }
    public void setSession(HttpServletRequest req) {
    	if(this.req!=null) throw new IllegalStateException("This context already has a session proxy.");
    	this.req = req;
    }
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
    	if(req==null) throw new NullPointerException("This context "+getId()+" doesn't have a session proxy.");
	return new HttpSessionFacade(req, timeout);
    }
    public static ContextManager addNew(HttpServletRequest req, HttpServletResponse res) {
    	ContextManager ctx = new ContextManager(req, res);
    	ctx.add();
    	ctx.setContext(new Context(res));
    	return ctx;
    }	

    
    private boolean removed=false;
    public synchronized void remove() {
    	super.remove();
    	req=null;
    }
    public String toString() {
	return super.toString() + ", has proxy: " +(req==null?"false":"true");
    }
    
	
}
