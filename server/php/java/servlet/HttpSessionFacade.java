/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.ISession;


public class HttpSessionFacade implements ISession {

    private HttpSession session;
    private int timeout;
    private HttpServletRequest req=null;
    private HttpSession sessionCache=null;
    private boolean isNew;
    private ServletContext ctx;
    private HttpServletResponse res;
    
    private HttpSession getSession() {
	if(sessionCache!=null) return sessionCache;
	sessionCache = session;
	sessionCache.setMaxInactiveInterval(timeout);
	return sessionCache;
    }
    public HttpSessionFacade (ServletContext ctx, HttpServletRequest req, HttpServletResponse res, int timeout) {
	this.session = req.getSession();
	this.req = req;
	this.ctx = ctx;
	this.res = res;
	this.timeout = timeout;
	this.isNew = session.isNew();
    }
    
    /**
     * Returns the HttpServletRequest
     * @return The HttpServletRequest.
     */
    public HttpServletRequest getHttpServletRequest() {
    	return this.req;
    }
    
    /**
     * Returns the ServletContext
     * @return The ServletContext.
     */
    public ServletContext getServletContext() {
        return this.ctx;
    }
    
    /**
     * Returns the ServletResponse
     * @return The ServletResponse.
     */
    public HttpServletResponse getHttpServletResponse() {
        return this.res;
    }
    /* (non-Javadoc)
     * @see php.java.bridge.ISession#get(java.lang.Object)
     */
    public Object get(Object ob) {
	return getSession().getAttribute(String.valueOf(ob));
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#put(java.lang.Object, java.lang.Object)
     */
    public void put(Object ob1, Object ob2) {
	getSession().setAttribute(String.valueOf(ob1), ob2);
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#remove(java.lang.Object)
     */
    public Object remove(Object ob) {
	String key = String.valueOf(ob);
	Object o = getSession().getAttribute(key);
	if(o!=null)
	    getSession().removeAttribute(key);
	return o;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#setTimeout(int)
     */
    public void setTimeout(int timeout) {
	getSession().setMaxInactiveInterval(timeout);
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#getTimeout()
     */
    public int getTimeout() {
	return getSession().getMaxInactiveInterval();
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#getSessionCount()
     */
    public int getSessionCount() {
	return -1;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#isNew()
     */
    public boolean isNew() {
	return isNew;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#destroy()
     */
    public void destroy() {
	getSession().invalidate();
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#putAll(java.util.Map)
     */
    public void putAll(Map vars) {
	for(Iterator ii = vars.keySet().iterator(); ii.hasNext();) {
	    Object key = ii.next();
	    Object val = vars.get(key);
	    put(key, val);
	}
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#getAll()
     */
    public Map getAll() {
	HttpSession session = getSession();
	HashMap map = new HashMap();
	for(Enumeration ee = session.getAttributeNames(); ee.hasMoreElements();) {
	    Object key = ee.nextElement();
	    Object val = get(key);
	    map.put(key, val);
	}
	return map;
    }
    public long getCreationTime() {
      return getSession().getCreationTime();
    }
    public long getLastAccessedTime() {
      return getSession().getLastAccessedTime();
    }
}
