/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.util.Map;

/**
 * The ISession interface is implemented by services to provide an
 * association between an HTTP client and HTTP server. This
 * association, or <em>session</em>, persists over multiple
 * connections and/or requests during a given time period.  Sessions
 * are used to maintain state and user identity across multiple page
 * requests.  <br> Example:<br>
 * 
 * $session=java_session();<br>
 * $val=$session-&gt;get("i");<br>
 * if(!$val) val = 0;
 * echo $val++; <br>
 * $session-&gt;put("i", new java("java.lang.Integer", $val));<br>
 * <P>An implementation of ISession represents the server's view
 * of the session. 
 *
 * When java_session() is called without a session name, the server
 * considers a session to be new until it has been joined by the
 * client.  Until the client joins the session, the isNew method
 * returns true. A value of true can indicate one of these three
 * cases: <UL> <LI>the client does not yet know about the session
 * <LI>the session has not yet begun <LI>the client chooses not to
 * join the session. This case will occur if the client supports only
 * cookies and chooses to reject any cookies sent by the server.  If
 * the server supports URL rewriting, this case will not commonly
 * occur.  </UL>
 *
 * <P>It is the responsibility of developers to design their
 * applications to account for situations where a client has not
 * joined a session. For example, in the following code snippet isNew
 * is called to determine whether a session is new. If it is, the
 * server will require the client to start a session by directing the
 * client to a welcome page <tt>welcomeURL</tt> where a user might be
 * required to enter some information and send it to the server before
 * gaining access to subsequent pages.
 * <P>
 * $session=java_session();<br>
 * if($session-&gt;isNew()) { <br>
 * &nbsp;&nbsp; header("Location: http://".$_SERVER['HTTP_HOST'] .dirname($_SERVER['PHP_SELF']) ."/welcomeURL.html");<br>
 * }<br>
 */
public interface ISession {
    /**
     * Returns the object bound to the given name in the session's
     * context layer data.  Returns null if there is no such
     * binding.
     *
     * @param name the name of the binding to find
     * @return the value bound to that name, or null if the binding does
     * not exist.
     * @exception IllegalStateException if an attempt is made to access 
     * session data after it has been invalidated
     */
    public Object get(Object name);
		
    /**
     * Binds the specified object into the session's context layer
     * data with the given name.  Any existing binding with the same
     * name is replaced.  
     *
     * @param name the name to which the data object will be bound.  This
     * parameter cannot be null.
     * @param value the data object to be bound.  This parameter cannot be null. 
     * @exception IllegalStateException if an attempt is made to access  
     * session data after the session has been invalidated.
     */
    public void put(Object name, Object value);
		
    /**
     * Removes the object bound to the given name in the session's
     * context layer data.  Does nothing if there is no object
     * bound to the given name.
     *
     * @param name the name of the object to remove
     * @exception IllegalStateException if an attempt is made to access 
     * session data after the session has been invalidated.
     */
    public Object remove(Object name);
		
    /**
     *
     * Specifies the time, in seconds, between client requests before
     * the servlet container will invalidate this session.  A negative
     * time indicates the session should never timeout.
     *
     * @param interval		An integer specifying the number
     * 				of seconds 
     *
     */
    public void setTimeout(int interval);
		
    /**
     * Returns the maximum time interval, in seconds, that the servlet
     * container will keep this session open between client
     * accesses. After this interval, the servlet container will
     * invalidate the session.  The maximum time interval can be set
     * with the <code>setTimeout</code> method.  A negative time
     * indicates the session should never timeout.
     *  
     *
     * @return		an integer specifying the number of
     *			seconds this session remains open
     *			between client requests
     *
     * @see		#setTimeout
     *
     *
     */
    public int getTimeout();
		
    /**
     * Returns the number of active sessions.
     * @return # of active sessions.
     */
    public int getSessionCount();
		
    /**
     * A session is considered to be "new" if it has been created by
     * the server, but the client has not yet acknowledged joining the
     * session. For example, if the server supported only cookie-based
     * sessions and the client had completely disabled the use of
     * cookies, then calls to JavaBridge.getSession() would always
     * return "new" sessions.
     *
     * @return true if the session has been created by the server but
     * the client has not yet acknowledged joining the session; false
     * otherwise
     * @exception IllegalStateException if an attempt is made to access  
     * session data after the session has been invalidated
     */
    public boolean isNew();
		
    /**
     * Causes this representation of the session to be invalidated an
     * removed from its context.
     *
     * @exception IllegalStateException if an attempt is made to
     * access session data after the session has been invalidated
     */
    public void destroy();
		
    /**
     * Copies all bindings to the session's context layer data.
     * Any existing binding with the same name is replaced.
     *
     * @param vars the map
     * parameter cannot be null.

     * @exception IllegalStateException if an attempt is made to
     * access session data after the session has been invalidated.
     */
    public void putAll(Map vars);

    /**
     * Returns a map of all bindings maintained by this session.

     * @return the map
     * @exception IllegalStateException if an attempt is made to access 
     * session data after it has been invalidated
     */
    public Map getAll();
}
