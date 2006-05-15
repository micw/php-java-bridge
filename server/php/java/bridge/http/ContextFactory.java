/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.util.HashMap;
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
 * A unique context
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
 * There can be only one instance of a ContextFactory per classloader.
 * </p>
 * @see php.java.servlet.ContextFactory
 * @see php.java.bridge.http.ContextServer
 */
public class ContextFactory extends SessionFactory {
    protected boolean removed=false;
    protected Object context = null;

    private static final HashMap contexts = new HashMap();
    private JavaBridge bridge = null;
	
    protected String id;
    private static synchronized String addNext(ContextFactory thiz) {
        int next = contexts.size()+1;
        String id = String.valueOf(next);
        contexts.put(id, thiz);
        return id;
    }
    private static synchronized void remove(String id) {
        contexts.remove(id);
    }
    protected ContextFactory() {
      super();
      id=addNext(this);
    }

    /**
     * Create a new ContextFactory and add it to the list of context factories kept by this classloader.
     * @return The created ContextFactory.
     * @see #get(String)
     */
    public static ContextFactory addNew() {
    	return new ContextFactory();
    }
    
    private ContextServer contextServer = null; 
   /**
     * Only for internal use.
     *  
     * Returns the context factory associated with the given <code>id</code>
     * @param id The ID
     * @param server Your context server.
     * @return The ContextFactory or null.
     * @see #addNew()
     * @throws SecurityException if id belongs to a different ContextServer.
     */
    /* See PhpJavaServlet#contextServer, http.ContextRunner#contextServer and 
     * JavaBridgeRunner#ctxServer. */
    public static ContextFactory get(String id, ContextServer server) {
        if(server == null) throw new NullPointerException("server");

        ContextFactory factory = (ContextFactory)contexts.get(id);
        if(factory==null) return null;
        
        if(factory.contextServer==null) factory.contextServer = server;
        if(factory.contextServer != server) throw new SecurityException("Illegal access");
        return factory;
    }
    /**
     * Override this method, if you want synchronize the current id with the persistent id.
     * @param target The fresh ContextFactory (which was passed via the current X_JAVABRIDGE_CONTEXT header).
     */
    protected void recycle(ContextFactory target) {}
    /**
     * Synchronize the current state with id.
     * <p>
     * When persistent connections are used, the bridge instances recycle their context factories. 
     * However, a jsr223 client may have passed a fresh context id. If this happened, the bridge calls this method 
     * which may update the current context with the fresh values from id.</p>
     * <p>This method automatically destroys the fresh context id</p>   
     * @param id The fresh id
     * @throws NullPointerException if the current id is not initialized
     * @see php.java.bridge.JavaBridge#recycle()
     */
    public void recycle(String id) throws SecurityException {
        ContextFactory target = get(id, contextServer);
        recycle(target);
        target.destroy();
    }
    /**
     * @deprecated
     * @see #destroy()
     */
    public void remove() {
        destroy();
    }
    /**
     * Removes the context factory from the classloader's list of context factories
     * and destroys its content.
     */
    public synchronized void destroy() {
	if(Util.logLevel>3) Util.logDebug("context finished: " +getId());
	remove(getId());
	bridge=null;
	removed=true;
	notify();
    }
    
    /**
     * @deprecated
     * @see #destroyAll()
     */
    public static void removeAll() {
        destroyAll();
    }

    /**
     * Remove all context factories from the classloader.
     * May only be called by the ContextServer.
     * @see php.java.bridge.http.ContextServer
     */
    public static synchronized void destroyAll() {
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
    /**
     * Return an emulated JSR223 context
     * @return The context
     * @see php.java.servlet.ContextFactory#getContext()
     * @see php.java.bridge.http.Context
     */
    public Object getContext() {
  	if(context==null) setContext(new Context());
	return context;
    }
	
    /**
     * Set the Context into this factory.
     * Should be called by Context.addNew() only.
     * @param context
     * @see #addNew()
     */
    protected void setContext(Object context) {
        if(this.context!=null) throw new IllegalStateException("ContextFactory already has a context");
        this.context = context;
    }
    /**
     * Set the JavaBridge into this context.
     * @param bridge The bridge to set.
     */
    public void setBridge(JavaBridge bridge) {
	if(this.bridge!=null) throw new IllegalStateException("ContextFactory already has a bridge");
	this.bridge = bridge;
    }
    /**
     * @return Returns the bridge.
     */
    public JavaBridge getBridge() {
        if(bridge != null) return bridge;
        setBridge(new JavaBridge());
        bridge.setClassLoader(new JavaBridgeClassLoader(bridge, null));
        bridge.setSessionFactory(this);
	return bridge;
    }
}
