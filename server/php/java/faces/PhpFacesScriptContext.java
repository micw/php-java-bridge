/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;
import php.java.script.HttpProxy;
import php.java.script.PhpScriptWriter;
import php.java.script.PhpSimpleHttpScriptContext;
import php.java.servlet.ContextFactory;

/**
 * A custom ScriptContext, keeps a ContextFactory and a HttpProxy.
 * @author jostb
 *
 */
public class PhpFacesScriptContext extends PhpSimpleHttpScriptContext {
    static {
	DynamicJavaBridgeClassLoader.initClassLoader(Util.DEFAULT_EXTENSION_DIR);
    }
    private Hashtable env;
    private ContextFactory ctx;
    private HttpProxy kont;
	
 
    public void initialize(ServletContext kontext,
			   HttpServletRequest req,
			   HttpServletResponse res,
			   PhpScriptWriter writer) throws ServletException {

    	ctx = PhpFacesScriptContextFactory.addNew(this, kontext, req, res);

    	env = new Hashtable();
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	this.env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	/* redirect to ourself */
	StringBuffer buf = new StringBuffer("127.0.0.1:");
	buf.append(req.getLocalPort());
	buf.append("/");
	buf.append(req.getRequestURI());
	buf.append(".php"); // it doesn't matter what we send here, as long as it ends with .php
	this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", buf.toString());
    	
    	super.initialize(kontext, req, res, writer);
    }

    /* (non-Javadoc)
     * @see php.java.script.IPhpScriptContext#getEnvironment()
     */
    public Map getEnvironment() {
        return env;
    }

    /* (non-Javadoc)
     * @see php.java.script.IPhpScriptContext#getContextFactory()
     */
    public php.java.bridge.http.ContextFactory getContextFactory() {
        return ctx;
    }

    /* (non-Javadoc)
     * @see php.java.script.IPhpScriptContext#setContinuation(php.java.script.HttpProxy)
     */
    public void setContinuation(HttpProxy kont) {
        this.kont = kont;
    }

    /* (non-Javadoc)
     * @see php.java.bridge.Invocable#call(php.java.bridge.PhpProcedureProxy)
     */
    public boolean call(PhpProcedureProxy kont) throws Exception {
	this.kont.call(kont);
	return true;
    }

}
