/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class Session{
    HashMap map;
    private String name;
    private static int sessionCount=0;
    boolean isNew=true;
    long startTime, timeout;
	
    public Object get(Object ob) {
	return map.get(ob);
    }
	
    public void put(Object ob1, Object ob2) {
	map.put(ob1, ob2);
    }
	
    public Object remove(Object ob) {
	return map.remove(ob);
    }
	
    public Session(String name) {
	this.name=name;
	this.sessionCount++;
	this.map=new HashMap();
	this.startTime=System.currentTimeMillis();
	this.timeout=1440000;
    }

    public void setTimeout(long timeout) {
	this.timeout=timeout;
    }
	
    public long getTimeout() {
	return timeout;
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
	
    public void destroySession() {
	destroy();
    }

    public void putAll(Map vars) {
	map.putAll(vars);
    }

    static void expire() {
	if(JavaBridge.sessionHash==null) return;
    	synchronized(JavaBridge.sessionHash) {
	    for(Iterator e = JavaBridge.sessionHash.values().iterator(); e.hasNext(); ) {
		Session ref = (Session)e.next();
		if((ref.timeout !=0) && (ref.startTime+ref.timeout<=System.currentTimeMillis())) {
		    sessionCount--;
		    JavaBridge.sessionHash.remove(ref.name);
		}
	    }
	}
    }
}
