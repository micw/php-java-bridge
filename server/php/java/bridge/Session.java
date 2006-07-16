/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class Session implements ISession {
  
    protected Map map;
    protected String name;
    private static int sessionCount=0;
    boolean isNew=true;
    protected long creationTime, lastAccessedTime, timeout;
	
    public Object get(Object ob) {
	this.lastAccessedTime=System.currentTimeMillis();
	return map.get(ob);
    }
	
    public void put(Object ob1, Object ob2) {
	this.lastAccessedTime=System.currentTimeMillis();
	map.put(ob1, ob2);
    }
	
    public Object remove(Object ob) {
	this.lastAccessedTime=System.currentTimeMillis();
	return map.remove(ob);
    }
	
    Session(String name) {
	this.name=name;
	Session.sessionCount++;
	this.map=Collections.synchronizedMap(new HashMap());
	this.creationTime = this.lastAccessedTime=System.currentTimeMillis();
	this.timeout=1440000;
    }

    public void setTimeout(int timeout) {
	this.timeout=timeout*1000;
	this.lastAccessedTime=System.currentTimeMillis();
    }
	
    public int getTimeout() {
	return (int)(timeout/1000);
    }
	
    public int getSessionCount() {
	return sessionCount;
    }
	
    public boolean isNew() {
	return isNew;
    }
	
    public void destroy() {
	sessionCount--;
	synchronized(JavaBridge.sessionHash) {
	    if(JavaBridge.sessionHash!=null)
		JavaBridge.sessionHash.remove(name);
	}
    }
	
    public void invalidate() {
	destroy();
    }

    public void putAll(Map vars) {
	this.lastAccessedTime=System.currentTimeMillis();
	map.putAll(vars);
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#getAll()
     */
	public Map getAll() {
	this.lastAccessedTime=System.currentTimeMillis();
	return new HashMap(map); // unshare the map 
    }

    /** Check for expired sessions every 10 minutes 
     * @see #CHECK_SESSION_TIMEOUT
     * @param bridge the bridge
     */
    static synchronized void expire() {
	if(JavaBridge.sessionHash==null) return;
    	synchronized(JavaBridge.sessionHash) {
	    for(Iterator e = JavaBridge.sessionHash.values().iterator(); e.hasNext(); ) {
		Session ref = (Session)e.next();
		if((ref.timeout >0) && (ref.lastAccessedTime+ref.timeout<=System.currentTimeMillis())) {
		    sessionCount--;
		    e.remove();
		    if(Util.logLevel>3) Util.logDebug("Session " + ref.name + " expired.");
		}
	    }
	}
    }
    
    /**
     * Expires all sessions immediately.
     *
     */
    public static void reset() {
	if(JavaBridge.sessionHash==null) return;
    	synchronized(JavaBridge.sessionHash) {
	    for(Iterator e = JavaBridge.sessionHash.values().iterator(); e.hasNext(); ) {
		Session ref = (Session)e.next();
		sessionCount--;
		e.remove();
		if(Util.logLevel>3) Util.logDebug("Session " + ref.name + " destroyed.");
	    }
	}
  	
    }

    public long getCreationTime() {
      return creationTime;
    }

    public long getLastAccessedTime() {
      return lastAccessedTime;
    }
}
