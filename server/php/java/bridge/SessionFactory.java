/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


public class SessionFactory {
    	
    /**
     * @param name The session name. If name is null, the session is an internal session
     * @param clientIsNew true if the client wants a new session
     * @param timeout timeout in seconds. If 0 the session does not expire.
     */
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
	if(name==null) name=JavaBridge.PHPSESSION; else name="@"+name;
	synchronized(JavaBridge.sessionHash) {
	    Session ref = null;
	    if(!JavaBridge.sessionHash.containsKey(name)) {
		ref = new Session(name);
	    } else {
		ref = (Session) JavaBridge.sessionHash.get(name);
		if(clientIsNew) { // client side gc'ed, destroy server ref now!
		    ref.destroy();
		    ref = new Session(name);
		} else {
		    ref.isNew=false;
		}
	    }
		    	    
	    ref.setTimeout(timeout);
	    JavaBridge.sessionHash.put(name, ref);
	    return ref;
	}
			
    }
	
    /**
     * Return the associated context
     * @return
     */
    public Object getContext() {
	return null;
    }
}
