/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.util.Map;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

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
 * System.out.println(((Invocable)e).invoke("java_get_server_name", new Object[]{}));<br>
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
    protected String name = null;
    
    /**
     * The continuation of the script
     */
    protected HttpProxy continuation = null;
    private StringReader localReader = null;

    private ScriptEngineFactory factory = null;

    /**
     * Create a new ScriptEngine.
     */
    public PhpScriptEngine() {
    }

    public PhpScriptEngine(PhpScriptEngineFactory factory) {
      this.factory = factory;
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object[])
     */
    public Object invoke(String methodName, Object[] args)
	throws ScriptException {
        if(scriptClosure==null) throw new ScriptException("The script from "+name+" is not invocable. To change this please add a line \"java_context()->call(java_closure());\" at the bottom of the script.");
	
	return invoke(scriptClosure, methodName, args);
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object, java.lang.Object[])
     */
    public Object invoke(Object thiz, String methodName, Object[] args)
	throws ScriptException, RuntimeException {
	PhpProcedure proc = (PhpProcedure)(Proxy.getInvocationHandler(thiz));
	try {
	    return proc.invoke(script, methodName, args);
	} catch (Throwable e) {
	    if(e instanceof RuntimeException) throw (RuntimeException)e;
	    Util.printStackTrace(e);
	    if(e instanceof Exception) throw new ScriptException(new Exception(e));
	    throw (RuntimeException)e;
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
      if(thiz==null) throw new NullPointerException("Object thiz cannot be null.");
      return ((PhpProcedureProxy)thiz).getNewFromInterface(clasz);
    }
    private void setName(String name) {
        int length = name.length();
        if(length>40) length=40;
        name = name.substring(length);
        this.name = name;
    }

    private Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        if(continuation != null) release();
  	if(reader==null) return null;      
        setName(name);
	try {
	    this.script = doEval(reader, context);
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
		
	if(this.script!=null) 
	try {
	    this.scriptClosure = this.script.getProxy(new Class[]{});
	} catch (Exception e) {
	    throw new ScriptException(e);
	}
	return null;
    }
    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return eval(reader, context, String.valueOf(reader));
   }
	
    protected HttpProxy getContinuation(Reader reader, ScriptContext context) {
    	IPhpScriptContext phpScriptContext = (IPhpScriptContext)context;
    	SessionFactory ctx = phpScriptContext.getContextFactory();
    	Map env = phpScriptContext.getEnvironment();
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
      	script = script.trim();
	return eval(this.localReader=new StringReader(script), context, String.valueOf(script));
    }

    /**@inheritDoc*/
    public ScriptEngineFactory getFactory() {
	return this.factory;
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
        ScriptContext scriptContext = new PhpScriptContext();
        
        if(namespace==null) namespace = createBindings();
        scriptContext.setBindings(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE),
				   ScriptContext.GLOBAL_SCOPE);
        
        return scriptContext;
    }    
    
    /**
     * Release the continuation
     */
    public void release() {
	if(continuation != null) {
	    if(localReader!=null) { try {localReader.close();} catch (Exception e) {Util.printStackTrace(e);} localReader=null; }
	    continuation.release();
	    continuation = null;
	    script = null;
	    scriptClosure = null;
	}
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#createBindings()
     */
    /** {@inheritDoc} */
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    /** {@inheritDoc} */
   public Object eval(Reader reader, Bindings bindings ) throws ScriptException {
        return eval(reader, getScriptContext(bindings));
    }
    
    /** {@inheritDoc} */
    public Object eval(String script, Bindings bindings) throws ScriptException {
        return eval(script , getScriptContext(bindings));
    }
    /** {@inheritDoc} */
    public Object eval(Reader reader) throws ScriptException {
        return eval(reader, getScriptContext(null));
    }
    /** {@inheritDoc} */
     public Object eval(String script) throws ScriptException {
        return eval(script, getScriptContext(null));
    }
 
 }
