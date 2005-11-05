/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;
import java.util.Hashtable;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

import php.java.bridge.ContextManager;
import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.Util;

public class PhpScriptContext extends SimpleScriptContext {
	static JavaBridgeRunner bridgeRunner;

	static {
		try {
			bridgeRunner = new JavaBridgeRunner();
		} catch (Exception e) {
			Util.printStackTrace(e);
		}
	}

	protected ContextManager ctx;
	private Hashtable env;
	
	public PhpScriptContext() {
	    env = new Hashtable();
		
	    ctx = PhpScriptContextManager.addNew(this);
	    JavaBridge bridge = new JavaBridge();
   	    ctx.setBridge(bridge);
	    bridge.setClassLoader(new JavaBridgeClassLoader(ctx.getBridge(), DynamicJavaBridgeClassLoader.newInstance(getClass().getClassLoader())));
	    bridge.setSessionFactory(ctx);
    	
	    /* send the session context now, otherwise the client has to 
	     * call handleRedirectConnection */
	    this.env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	    /* the client should connect back to us */
	    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",Util.getHostAddress()+":"+bridgeRunner.getSocket().getSocketName());
	    /* the client should call continuation.call($kont) with its current continuation $kont as the argument */
	    this.env.put("X_JAVABRIDGE_CONTINUATION", String.valueOf(ScriptContext.ENGINE_SCOPE));
	}

	/**
	 * @return Returns the context.
	 */
	public ContextManager getContext() {
		return ctx;
	}

	public Writer getWriter() {
		return new PhpScriptWriter(System.out);
	}
	/**
	 * @return
	 */
	public Hashtable getEnvironment() {
		return env;
	}
	
	public ContextManager getContextManager() {
		return ctx;
	}
}
