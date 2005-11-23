/*-*- mode: Java; tab-width:8 -*-*/

package javax.script.http;

import javax.script.ScriptEngine;
import javax.servlet.GenericServlet;

/**
 * @author jostb
 *
 */
public abstract class HttpScriptServlet extends GenericServlet {

	
	/**
	 * Returns a HttpScriptContext initialized using the specified HttpServletRequest, HttpServletResponse and a reference to this HttpScriptServlet
	 * @param req - The specified HttpServletRequest.
	 * @param res - The specified HttpServletResponse.
	 * @return the initialized HttpScriptContext.
	 * @throws javax.servlet.ServletException
	 */
	public abstract HttpScriptContext getContext(javax.servlet.http.HttpServletRequest req,
            javax.servlet.http.HttpServletResponse res)
     throws javax.servlet.ServletException;
	 
	/**
	 * Returns a ScriptEngine that is used by the HttpScriptServlet to execute a single request.
	 * @param request - The current request.
	 * @return The ScriptEngine used by this HttpScriptServlet to execute requests.
	 */
	public abstract ScriptEngine getEngine(javax.servlet.http.HttpServletRequest request);
	
	/**
	 * Called to indicate that a ScriptEngine retruned by a call to getEngine is no longer in use.
	 * @param engine - The ScriptEngine
	 */
	public abstract void releaseEngine(ScriptEngine engine);
	
}
