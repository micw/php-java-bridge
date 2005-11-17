/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.util.Hashtable;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import php.java.bridge.NotImplementedException;
import php.java.bridge.PhpProcedure;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;

/**
 * This class implements the ScriptEngine.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = new PhpScriptEngine();<br>
 * e.eval(new URLReader(new URL("http://localhost/foo.php"));<br>
 * System.out.println(((Invocable)e).call("java_get_server_name", new Object[]{}));<br>
 * e.release();<br>
 * </code>
 * @author jostb
 *
 */
public class PhpScriptEngine extends AbstractScriptEngine implements Invocable {

	
    /**
     * The allocated script
     */
    protected PhpProcedureProxy script = null;
    protected Object scriptClosure = null;
	
    /**
     * The continuation of the script
     */
    protected HttpProxy continuation = null;

    /**
     * @param request
     */
    public PhpScriptEngine() {
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object[])
     */
    public Object call(String methodName, Object[] args)
	throws ScriptException {
	return call(methodName, scriptClosure, args);
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object, java.lang.Object[])
     */
    public Object call(String methodName, Object thiz, Object[] args)
	throws ScriptException {
	PhpProcedure proc = (PhpProcedure)(Proxy.getInvocationHandler(thiz));
	try {
	    return proc.invoke(script, methodName, args);
	} catch (Throwable e) {
	    throw new ScriptException(new Exception(e));
	}
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#getInterface(java.lang.Class)
     */
    public Object getInterface(Class clasz) {
	return getInterface(script, clasz);
    }
    /* (non-Javadoc)
     * @see javax.script.Invocable#getInterface(java.lang.Object, java.lang.Class)
     */
    public Object getInterface(Object thiz, Class clasz) {
	return ((PhpProcedureProxy)thiz).getProxy(new Class[]{clasz});
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
	try {
	    this.script = doEval(reader, context);
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
		
	if(this.script==null) throw new ScriptException("This script is not invocable. To change this please add a line \"java_context()->call(java_closure());\" at the bottom of the script.");
		
	try {
	    this.scriptClosure = this.script.getProxy(new Class[]{});
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
	return null;
    }
	
    protected HttpProxy getContinuation(Reader reader, ScriptContext context) {
    	PhpScriptContext phpScriptContext = (PhpScriptContext)context;
    	SessionFactory ctx = phpScriptContext.getContextManager();
    	Hashtable env = phpScriptContext.getEnvironment();
    	HttpProxy kont = new HttpProxy(reader, env, ctx, ((PhpScriptWriter)(context.getWriter())).getOutputStream()); 
     	phpScriptContext.setContinuation(kont);
	return kont;
    }

    /*
     * Obtain a PHP instance for url.
     */
    protected PhpProcedureProxy doEval(Reader reader, ScriptContext context) throws UnknownHostException, IOException, InterruptedException {
    	continuation = getContinuation(reader, context);

     	continuation.start();
    	return continuation.getPhpScript();
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.lang.String, javax.script.ScriptContext)
     */
    public Object eval(String script, ScriptContext context)
	throws ScriptException {
	try {
	    return eval(new FileReader(new File(script)), context);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    throw new ScriptException(e);
	}
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#getFactory()
     */
    public ScriptEngineFactory getFactory() {
	throw new NotImplementedException();
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
        ScriptContext scriptContext = new PhpScriptContext();
        
        if(namespace==null) namespace = this.namespace;
        scriptContext.setNamespace(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setNamespace(this.globalspace,
				   ScriptContext.GLOBAL_SCOPE);
        
        return scriptContext;
    }    
    
    /**
     * Release the continuation
     */
    public void release() {
	if(continuation != null) {
	    continuation.stopContinuation();
	    continuation = null;
	    script = null;
	    scriptClosure = null;
	}
    }

}
