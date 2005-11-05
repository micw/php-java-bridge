/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.http;

import javax.script.ScriptEngine;
import javax.script.http.SimpleHttpScriptContext;
import javax.script.http.HttpScriptContext;
import javax.script.http.HttpScriptServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jostb
 *
 */
public abstract class PhpHttpScriptServlet extends HttpScriptServlet {

    public void init(ServletConfig config) throws ServletException {
	super.init(config);
	SimpleHttpScriptContext.initializeGlobal(config);
    }
    /* (non-Javadoc)
     * @see javax.script.http.HttpScriptServlet#getContext(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public HttpScriptContext getContext(HttpServletRequest req,
					HttpServletResponse res) throws ServletException {
        
	PhpHttpScriptContext context = new PhpHttpScriptContext();
        context.initialize(this, req, res);
        return context;		
    }

    /* (non-Javadoc)
     * @see javax.script.http.HttpScriptServlet#getEngine(javax.servlet.http.HttpServletRequest)
     */
    public ScriptEngine getEngine(HttpServletRequest request) {
	return new PhpHttpScriptEngine(request);
    }

    /* (non-Javadoc)
     * @see javax.script.http.HttpScriptServlet#releaseEngine(javax.script.ScriptEngine)
     */
    public void releaseEngine(ScriptEngine engine) {
	((PhpHttpScriptEngine)engine).release();
    }

    /* (non-Javadoc)
     * @see javax.script.http.HttpScriptServlet#getContext(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public HttpScriptContext getContext(ServletRequest req, ServletResponse res) throws ServletException, ClassCastException {
	return getContext((HttpServletRequest) req, (HttpServletResponse)res);
    }

    /* (non-Javadoc)
     * @see javax.script.http.HttpScriptServlet#getEngine(javax.servlet.http.HttpServletRequest)
     */
    public ScriptEngine getEngine(ServletRequest req) throws ClassCastException {
	return getEngine((HttpServletRequest) req);
    }

}
