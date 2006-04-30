/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * Create new session or context instances
 * @see php.java.bridge.Session
 * @see php.java.bridge.http.Context
 * @see php.java.servlet.Context
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.servlet.ContextFactory
 * @see php.java.script.PhpScriptContextFactory
  * @author jostb
 *
 */
public class SessionFactory {
  
  private ISession session(String name, boolean clientIsNew, int timeout) {
	synchronized(JavaBridge.sessionHash) {
	    Session ref = null;
	    if(!JavaBridge.sessionHash.containsKey(name)) {
		ref = new Session(name);
		JavaBridge.sessionHash.put(name, ref);
	    } else {
		ref = (Session) JavaBridge.sessionHash.get(name);
		if(clientIsNew) { // client side gc'ed, destroy server ref now!
		    ref.destroy();
		    ref = new Session(name);
		    JavaBridge.sessionHash.put(name, ref);
		} else {
		    ref.isNew=false;
		}
	    }
		    	    
	    ref.setTimeout(timeout);
	    return ref;
	}
  }
  /**
   * @param name The session name. If name is null, the session is an internal session
   * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   */
  public ISession getSession(String name, boolean clientIsNew, int timeout) {
	if(name==null) name=JavaBridge.PHPSESSION; else name="@"+name;
	return session(name, clientIsNew, timeout);
  }

  /**
   * @param name The session name. If name is null, the session is an internal session
   * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   */
  public ISession getSessionInternal(boolean clientIsNew, int timeout) {
	String name=JavaBridge.INTERNAL_PHPSESSION;
	return session(name, clientIsNew, timeout);
  }
		
	
    /**
     * Return the associated context
     * @return The context
     */
    public Object getContext() {
	return null;
    }
}
