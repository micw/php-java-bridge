/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.Hashtable;
import java.util.Iterator;

import php.java.bridge.Context;

public class ContextManager extends SessionFactory {
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
    protected ContextManager() {
	id=String.valueOf(0xFFFF&getNext());
    }

    protected void add() {
    	Object old = contexts.put(this.getId(), this);
    	if(old!=null) {
    	    ((ContextManager)old).remove();
    	    if(Util.logLevel>2) Util.logError("Removed stale context: " +old);
    	}
    }
    public static ContextManager addNew() {
    	ContextManager ctx = new ContextManager();
    	ctx.add();
    	ctx.setContext(new Context());
    	return ctx;
    }	

    public static ContextManager get(String id) {
   	return (ContextManager)contexts.get(id);
    }
    
    private boolean removed=false;
    public synchronized void remove() {
	contexts.remove(getId());
	bridge=null;
	removed=true;
	notify();
    }
    public static void removeAll() {
	for(Iterator ii=contexts.values().iterator(); ii.hasNext();) {
	    ContextManager ctx = ((ContextManager)ii.next());
	    synchronized(ctx) {
		ctx.removed=true;
		ctx.notify();
	    }
	    ii.remove();
	}
    }
    public synchronized void waitFor() throws InterruptedException {
    	if(!removed) wait();
    }
    public String getId() { 
	return id; 
    }
    public String toString() {
	return "Context# " +id;
    }
	public Object getContext() {
		return context;
	}
	
	public void setContext(Object context) {
		this.context = context;
	}
	/**
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
