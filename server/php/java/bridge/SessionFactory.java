/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.util.Iterator;
import java.util.LinkedList;

import php.java.bridge.Util.Thread;
import php.java.bridge.http.IContext;

/**
 * Create new session instances
 * @see php.java.bridge.Session
 * @see php.java.bridge.http.Context
 * @see php.java.servlet.Context
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.servlet.ServletContextFactory
 * @see php.java.script.PhpScriptContextFactory
 * @author jostb
 *
 */
public class SessionFactory extends JavaBridgeFactory {

  /** Check for expired sessions or contexts every 10 minutes */
  public static final long TIMER_DURATION = 600000;

  private static final SessionTimer timer = new SessionTimer();
  
  public SimpleJavaBridgeClassLoader getJavaBridgeClassLoader() {
      if (javaBridgeClassLoader!=null) return javaBridgeClassLoader;
      return javaBridgeClassLoader=new SimpleJavaBridgeClassLoader(getClassLoader());
  }
  public ClassLoader getClassLoader() {
      return Util.getContextClassLoader();
  }
  
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
   * Return a session.
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
   * Return an anonymous session for internal use.
   * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   * @return The session
   */
  public ISession getSession(boolean clientIsNew, int timeout) {
	String name=JavaBridge.INTERNAL_PHPSESSION;
	return session(name, clientIsNew, timeout);
  }
		
	
    /**
     * Return the associated context
     * @return Always null
     * @see php.java.bridge.http.ContextFactory#getContext()
     */
    public IContext getContext() {
	return null;
    }
    protected static final SessionTimer getTimer() {
        return timer;
    }
    
    /** Only for internal use */
    public static final void destroyTimer() {
	    getTimer().interrupt();
    }
    protected static class SessionTimer implements Runnable {
        LinkedList jobs = new LinkedList();
	private Thread thread;
        public SessionTimer() {

/*
 Must not access Util at this point
            if (Util.logLevel>5) 
        	    System.out.println("lifecycle: init session timer "+System.identityHashCode(SessionFactory.class));
*/

	    thread = (new Util.Thread(this, "JavaBridgeSessionTimer"));
	    thread.start();
        }
        public void addJob(Runnable r) {
            jobs.add(r);
        }
        public void interrupt () {
	    if (Util.logLevel>5)
		    System.out.println ("lifecycle: sending session timer interrupt " + +System.identityHashCode(SessionFactory.class));

	    thread.interrupt();
        }
        public void run() {
            try {
                while(!java.lang.Thread.interrupted()) {
                    Thread.sleep(TIMER_DURATION);
                    Session.expire();
                    
                    for(Iterator ii = jobs.iterator(); ii.hasNext();) {
                        Runnable job = (Runnable) ii.next();
                        job.run();
                    }
                }
            } catch (InterruptedException e) {
		if (Util.logLevel>5) 
		    System.out.println ("lifecycle: session timer got interrupt"+System.identityHashCode(SessionFactory.class));
	    }
	    if (Util.logLevel>5) 
		    System.out.println ("lifecycle: session timer terminating"+System.identityHashCode(SessionFactory.class));
        }
    }
}
