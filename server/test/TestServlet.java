/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.io.IOException;
import java.net.URL;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.http.HttpScriptContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import php.java.bridge.Util;
import php.java.script.URLReader;
import php.java.script.http.PhpHttpScriptServlet;

/**
 * @author jostb
 *
 */
public class TestServlet extends PhpHttpScriptServlet {

    /* (non-Javadoc)
     * @see javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void service(ServletRequest req, ServletResponse res)
	throws ServletException, IOException {

	try {
				
	    ScriptEngine engine = getEngine(req);
	    HttpScriptContext context = getContext(req, res);

	    engine.eval(new URLReader(new URL("http://localhost:8080/JavaBridge/foo.php")), context);
	    ((Invocable)engine).call("hashCode", new Object[]{});
	    context.getResponse().getWriter().println( ((Invocable)engine).call("hashCode", new Object[]{})+ "<br>\n");
	    context.getResponse().getWriter().println( ((Invocable)engine).call("hashCode", new Object[]{})+ "<br>\n");
	    context.getResponse().getWriter().println( ((Invocable)engine).call("hashCode", new Object[]{})+ "<br>\n");
	    releaseEngine(engine);
	    context.release();
				
	} catch (ScriptException e) {
	    Util.printStackTrace(e);
	}
		
    }


}
