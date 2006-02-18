/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.util.Hashtable;
import java.util.Iterator;

import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;


/**
 * Create session, jsr223 contexts.<p>
 * The ContextFactory may keep a promise (a "proxy") which one may evaluate to a
 * session reference (for PHP/JSP session sharing), and/or it may
 * reference a "half-executed" bridge for local channel re-directs (for
 * "high speed" communication links). 
 *
 * ContextFactory instances are kept in an array with no more than 65535
 * entries, after 65535 context creations the context factory is destroyed if
 * this did not happen explicitly.  A unique [1..65535] context
 * instance should be created for each request and destroyed when the request
 * is done.  
 * 
 * The string ID of the instance should be passed to the client, which may
 * pass it back together with the getSession request or the "local
 * channel re-direct". If the former happens, we invoke the promise
 * and return the session object to the client. Different promises can
 * evaluate to the same session object.  For local channel re-directs
 * the ContextFactory is given to a ContextRunner which handles the
 * local channel communication.
 * <p>
 * There can be only one instance of a ContextFactory per VM classloader.
 * </p>
 * @see php.java.servlet.ContextFactory
 * @see php.java.bridge.http.ContextServer
 */
public class ContextFactory extends SessionFactory {
    protected boolean removed=false;
    protected Object context;

    static final Hashtable contexts = new Hashtable();
    private JavaBridge bridge;
	
    private static short count=0; // If a context survives more than 65535
    // context creations, that context will be
    // destroyed.
    protected String id;
    protected static synchronized int getNext() {
	if(++count==0) ++count;
	return count;
    }
    protected ContextFactory() {
      super();
      id=String.valueOf(0xFFFF&getNext());
      setBridge(new JavaBridge());
      bridge.setClassLoader(new JavaBridgeClassLoader(bridge, null));
      bridge.setSessionFactory(this);
      //JavaBridge.load++;
    }

    protected void add() {
    	Object old = contexts.put(this.getId(), this);
    	if(old!=null) {
    	    ((ContextFactory)old).remove();
    	    if(Util.logLevel>2) Util.logError("Removed stale context: " +old);
    	}
    }
    
    /**
     * Create a new ContextFactory and add it to the list of context factories kept by this VM.
     * @return The created ContextFactory.
     * @see #get(String)
     */
    public static ContextFactory addNew() {
    	ContextFactory ctx = new ContextFactory();
    	ctx.add();
    	ctx.setContext(new Context());
    	return ctx;
    }	

    /**
     * Returns the context factory associated with the given <code>id</code>
     * @param id The ID
     * @return The ContextFactory or null.
     * @see #addNew()
     */
    public static ContextFactory get(String id) {
   	return (ContextFactory)contexts.get(id);
    }
    
    /**
     * Removes the context factory from the VM list of context factories.
     *
     */
    public synchronized void remove() {
	if(bridge.logLevel>3) bridge.logDebug("context finished: " +getId());
	contexts.remove(getId());
	bridge=null;
	removed=true;
	notify();
    }
    
    /**
     * Remove all context factories from the VM.
     * May only be called by the ContextServer.
     * @see php.java.bridge.http.ContextServer
     */
    public static void removeAll() {
	for(Iterator ii=contexts.values().iterator(); ii.hasNext();) {
	    ContextFactory ctx = ((ContextFactory)ii.next());
	    synchronized(ctx) {
		ctx.removed=true;
		ctx.notify();
	    }
	    ii.remove();
	}
    }
    
    /**
     * Wait until this context is finished.
     * @throws InterruptedException
     * @see php.java.bridge.http.ContextRunner
     */
    public synchronized void waitFor() throws InterruptedException {
    	if(!removed) wait();
    }
    
    /**
     * Return the serializable ID of the context manager
     * @return The ID
     */
    public String getId() { 
	return id; 
    }
    public String toString() {
	return "Context# " +id;
    }
    public Object getContext() {
	return context;
    }
	
    /**
     * Set the Context into this factory.
     * Should be called by Context.addNew() only.
     * @param context
     * @see #addNew()
     */
    protected void setContext(Object context) {
	this.context = context;
    }
    /**
     * Set the JavaBridge into this context.
     * @param bridge The bridge to set.
     */
    public void setBridge(JavaBridge bridge) {
	if(this.bridge!=null) throw new IllegalStateException("Context already has a bridge");
	this.bridge = bridge;
    }
    /**
     * @return Returns the bridge.
     */
    public JavaBridge getBridge() {
	return bridge;
    }
}
