/**
 * 
 */
package php.java.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

final class RemoteHttpServletRequest extends HttpServletRequestWrapper {

    private ServletContextFactory factory;
    public RemoteHttpServletRequest(ServletContextFactory factory, HttpServletRequest arg0) {
	super(arg0);
	this.factory = factory;
    }

    public HttpSession getSession() {
	return factory.getSession();
    }

    public HttpSession getSession(boolean arg0) {
	//FIXME: implement in all places
	return getSession();
    }

}