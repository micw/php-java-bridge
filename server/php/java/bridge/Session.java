/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Session implements ISession {
    protected Map map;
    protected String name;
    private static int sessionCount=0;
    boolean isNew=true;
    protected long startTime, timeout;
	
    public Object get(Object ob) {
	return map.get(ob);
    }
	
    public void put(Object ob1, Object ob2) {
	map.put(ob1, ob2);
    }
	
    public Object remove(Object ob) {
	return map.remove(ob);
    }
	
    Session(String name) {
	this.name=name;
	Session.sessionCount++;
	this.map=Collections.synchronizedMap(new HashMap());
	this.startTime=System.currentTimeMillis();
	this.timeout=1440000;
    }

    public void setTimeout(int timeout) {
	this.timeout=timeout*1000;
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
	map.putAll(vars);
    }

    /* (non-Javadoc)
     * @see php.java.bridge.ISession#getAll()
     */
    public Map getAll() {
	return new HashMap(map); // unshare the map 
    }

    static void expire(JavaBridge bridge) {
	if(JavaBridge.sessionHash==null) return;
    	synchronized(JavaBridge.sessionHash) {
	    for(Iterator e = JavaBridge.sessionHash.values().iterator(); e.hasNext(); ) {
		Session ref = (Session)e.next();
		if((ref.timeout >0) && (ref.startTime+ref.timeout<=System.currentTimeMillis())) {
		    sessionCount--;
		    e.remove();
		    if(bridge.logLevel>3) bridge.logDebug("Session " + ref.name + " expired.");
		}
	    }
	}
    }
    
    static void reset(JavaBridge bridge) {
	if(JavaBridge.sessionHash==null) return;
    	synchronized(JavaBridge.sessionHash) {
	    for(Iterator e = JavaBridge.sessionHash.values().iterator(); e.hasNext(); ) {
		Session ref = (Session)e.next();
		sessionCount--;
		e.remove();
		if(bridge.logLevel>3) bridge.logDebug("Session " + ref.name + " destroyed.");
	    }
	}
  	
    }
}
