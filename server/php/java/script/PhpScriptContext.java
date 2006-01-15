/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;
import java.util.Hashtable;
import java.util.Map;

import javax.script.SimpleScriptContext;

import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;
import php.java.bridge.http.ContextFactory;

/**
 * This class implements a simple script context for PHP. It starts a standalone 
 * <code>JavaBridgeRunner</code> which listens for requests from php instances.<p>
 * 
 * In a servlet environment please use a <code>php.java.script.PhpSimpleHttpScriptContext</code> instead.
 * @see php.java.script.PhpSimpleHttpScriptContext
 * @see php.java.bridge.JavaBridgeRunner
 * @author jostb
 *
 */
public class PhpScriptContext extends SimpleScriptContext implements IPhpScriptContext {
    static JavaBridgeRunner bridgeRunner;

    static {
	try {
	    bridgeRunner = new JavaBridgeRunner();
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }

    protected ContextFactory ctx;
    private Hashtable env;
    private HttpProxy kont;
	
    /**
     * Create a standalone PHP script context.
     *
     */
    public PhpScriptContext() {
	env = new Hashtable();
		
	ctx = PhpScriptContextFactory.addNew(this);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	this.env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	/* the client should connect back to us */
	this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",Util.getHostAddress()+":"+bridgeRunner.getSocket().getSocketName());
    }


    public Writer getWriter() {
	return new PhpScriptWriter(System.out);
    }
	
    /**
     * @return the environment
     */
    public Map getEnvironment() {
	return env;
    }
	
    /**
     * 
     * @return the context manager
     */
    public ContextFactory getContextFactory() {
	return ctx;
    }

    /**
     * Set the php continuation
     * @param kont - The continuation.
     */
    public void setContinuation(HttpProxy kont) {
	this.kont = kont;
    }


    /* (non-Javadoc)
     * @see php.java.bridge.Invocable#call(php.java.bridge.PhpProcedureProxy)
     */
    public boolean call(PhpProcedureProxy kont) throws InterruptedException {
	this.kont.call((PhpProcedureProxy)kont);
	return true;
    }
}
