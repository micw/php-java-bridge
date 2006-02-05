/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;

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
    static JavaBridgeRunner bridgeRunner = null;

    static {
	try {
	    bridgeRunner = new JavaBridgeRunner();
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }

    protected ContextFactory ctx;
    private HttpProxy kont;
	
    /**
     * Create a standalone PHP script context.
     *
     */
    public PhpScriptContext() {
        super();
    }


    public Writer getWriter() {
	return new PhpScriptWriter(System.out);
    }
	
    /**
     * 
     * @return the context factory
     */
    public ContextFactory getContextFactory() {
	return ctx;
    }

    /**
     * Set the context factory.
     * @param ctx
     */
    public void setContextFactory(ContextFactory ctx) {
        this.ctx = ctx;
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
    /**@inheritDoc*/
    public boolean call(PhpProcedureProxy kont) throws InterruptedException {
	this.kont.call(kont);
	return true;
    }


    public JavaBridgeRunner getHttpServer() {
        return bridgeRunner;
    }
}
