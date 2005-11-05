/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.http;

import java.io.Reader;
import java.net.URL;
import java.util.Hashtable;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.http.HttpScriptContext;
import javax.servlet.http.HttpServletRequest;

import php.java.bridge.NotImplementedException;
import php.java.bridge.SessionFactory;
import php.java.script.HttpProxy;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptWriter;
import php.java.script.URLReader;

/**
 * @author jostb
 *
 */
public class PhpHttpScriptEngine extends PhpScriptEngine implements Invocable {

    private HttpServletRequest request;
	
	
    /**
     * @param request
     */
    public PhpHttpScriptEngine(HttpServletRequest request) {
	super();
	this.request = request;
    }

    protected HttpProxy getContinuation(Reader reader, ScriptContext context) throws ClassCastException {
	URL url = ((URLReader)reader).getURL();
    	PhpHttpScriptContext phpScriptContext = (PhpHttpScriptContext)context;
    	SessionFactory ctx = phpScriptContext.getContextManager();
    	Hashtable env = phpScriptContext.getEnvironment();
	HttpProxy kont = new HttpProxy(reader, env, ctx, ((PhpScriptWriter)context.getWriter()).getOutputStream());
	context.setAttribute("X_JAVABRIDGE_CONTINUATION", kont, HttpScriptContext.REQUEST_SCOPE);
    	return kont;
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#getFactory()
     */
    public ScriptEngineFactory getFactory() {
	throw new NotImplementedException();
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
	throw new IllegalStateException("The HTTP script context must be obtained from the Servlet");
    }

}
