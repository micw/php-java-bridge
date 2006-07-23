/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Create new session or context instances
 * @see php.java.bridge.Session
 * @see php.java.bridge.http.Context
 * @see php.java.servlet.Context
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.servlet.ServletContextFactory
 * @see php.java.script.PhpScriptContextFactory
  * @author jostb
 *
 */
public class SessionFactory {

  /** Check for expired sessions or contexts every 10 minutes */
  public static final long TIMER_FREQ = 600000;
  private static final SessionTimer timer = new SessionTimer();
  
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
   * @param name The session name. If name is null, the name PHPSESSION will be used.
   * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   * @return The session
   * @see php.java.bridge.ISession
   */
  public ISession getSession(String name, boolean clientIsNew, int timeout) {
	if(name==null) name=JavaBridge.PHPSESSION; else name="@"+name;
	return session(name, clientIsNew, timeout);
  }

  /**
    * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   * @return The session
   */
  protected ISession getSessionInternal(boolean clientIsNew, int timeout) {
	String name=JavaBridge.INTERNAL_PHPSESSION;
	return session(name, clientIsNew, timeout);
  }
		
	
    /**
     * Return the associated context
     * @return Always null
     * @see php.java.bridge.http.ContextFactory#getContext()
     */
    public Object getContext() {
	return null;
    }
    protected static final SessionTimer getTimer() {
        return timer;
    }
    protected static class SessionTimer implements Runnable {
        LinkedList jobs = new LinkedList();
        public SessionTimer() {
          Thread t = (new Thread(this, "JavaBridgeSessionTimer"));
          t.setDaemon(true);
          t.start();
        }
        public void addJob(Runnable r) {
            jobs.add(r);
        }
        public void run() {
            try {
                while(true) {
                    Thread.sleep(TIMER_FREQ);
                    Session.expire();
                    
                    for(Iterator ii = jobs.iterator(); ii.hasNext();) {
                        Runnable job = (Runnable) ii.next();
                        job.run();
                    }
                }
            } catch (InterruptedException e) {/*ignore*/}
        }
    }

    /**
     * Recycle the factory for new reqests.
     */
    public void recycle() {
    }

    /**
     * Destroy the factory
     */
    public void destroy() {
    }
}
