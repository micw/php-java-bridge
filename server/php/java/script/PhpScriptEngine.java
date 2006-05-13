/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;
import java.util.HashMap;
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
import php.java.bridge.Util;
import php.java.bridge.http.ContextFactory;

/**
 * This class implements the ScriptEngine.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = (new ScriptEngineManager()).getEngineByName("php");<br>
 * e.eval(new URLReader(new URL("http://localhost/foo.php"));<br>
 * System.out.println(((Invocable)e).invoke("java_get_server_name", new Object[]{}));<br>
 * e.eval((String)null);<br>
 * </code><br>
 * When using an URLReader the external foo.php must contain a call() back into our java continuation at the end of the script:<br>
 * <code>
 * &lt;?php java_context->call(java_closure()) ?&gt;<br>
 * </code><br>
 * And the php.ini must contain a java.servlet setting, so that the bridge selects the servlet- instead of the standalone back-end. Example:<br>
 * <code>
 * extension=java.so<br>
 * ;;on windows use: extension=php_java.dll<br>
 * [java]<br>
 * java.servlet=On<br>
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
    protected final HashMap processEnvironment = getProcessEnvironment();
    protected Map env = null;
    
    private ScriptEngineFactory factory = null;

    protected void initialize() {
      setContext(getPhpScriptContext());
    }

    private static final Class[] EMPTY_PARAM = new Class[0];
    private static final Object[] EMPTY_ARG = new Object[0];
    private static final File winnt = new File("c:/winnt");
    private static final File windows = new File("c:/windows");

    /**
     * Get the current process environment which will be passed to the sub-process.
     * Requires jdk1.5 or higher. In jdk1.4, where System.getenv() is not available,
     * we allocate an empty map.<p>
     * To add custom environment variables (such as PATH=... or LD_ASSUME_KERNEL=2.4.21, ...),
     * use a custom PhpScriptEngine, for example:<br>
     * <code>
     * public class MyPhpScriptEngine extends PhpScriptEngine {<br>
     * &nbsp;&nbsp;protected HashMap getProcessEnvironment() {<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;HashMap map = new HashMap();<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;map.put("PATH", "/usr/local/bin");<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;return map; <br>
     * &nbsp;&nbsp;}<br>
     * }<br>
     * </code>
     * @return The current process environment.
     */
    protected HashMap getProcessEnvironment() {
	HashMap defaultEnv = new HashMap();
      String val = null;
	// Bug in WINNT and WINXP.
	// If SystemRoot is missing, php cannot access winsock.
	if(winnt.isDirectory()) val="c:\\winnt";
	else if(windows.isDirectory()) val = "c:\\windows";
	try {
	    String s = System.getenv("SystemRoot"); 
	    if(s!=null) val=s;
      } catch (Throwable t) {/*ignore*/}
      try {
	    String s = System.getProperty("Windows.SystemRoot");
	    if(s!=null) val=s;
      } catch (Throwable t) {/*ignore*/}
	if(val!=null) defaultEnv.put("SystemRoot", val);
      try {
        Method m = System.class.getMethod("getenv", EMPTY_PARAM);
        Map map = (Map) m.invoke(System.class, EMPTY_ARG);
        defaultEnv.putAll(map);
    } catch (Exception e) {/*ignore*/}
    return defaultEnv;
  }

    /**
     * Create a new ScriptEngine with a default context.
     */
    public PhpScriptEngine() {
        initialize(); 	// initialize it here, otherwise we have to override all inherited methods.
        		// needed because parent (JDK1.6) initializes and uses a protected context field 
        		// instead of calling getContext() when appropriate.
    }

    /**
     * Create a new ScriptEngine from a factory.
     * @param factory The factory
     * @see #getFactory()
     */
    public PhpScriptEngine(PhpScriptEngineFactory factory) {
        this();
        this.factory = factory;
    }

    /* Revert constructor chain. Call super(false); privateInit(); super.initialize(), 
     * see PhpFacesScriptEngine constructor and PhpScriptEngine() constructor. -- The jsr223 API is really odd ... */
    protected PhpScriptEngine(boolean initialize) {
        if(initialize) initialize();
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
        if(length>160) length=160;
        name = name.substring(0, length);
        this.name = name;
    }

    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
        ContextFactory ctx;
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = PhpScriptContextFactory.addNew((ScriptContext)context);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	/* the client should connect back to us */
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",Util.getHostAddress()+":"+PhpScriptContext.getHttpServer().getSocket().getSocketName());

    }
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        if(continuation != null) release();
  	if(reader==null) return null;
  	
  	setNewContextFactory();
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
    	HttpProxy kont = new HttpProxy(reader, env, ((PhpScriptWriter)(context.getWriter())).getOutputStream()); 
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
    /**@inheritDoc*/
    public Object eval(String script, ScriptContext context)
	throws ScriptException {
      	script = script.trim();
	Reader localReader = new StringReader(script);
	Object obj = eval(localReader, context, String.valueOf(script));
	try { localReader.close(); } catch (IOException e) {/*ignore*/}
	return obj;
    }

    /**@inheritDoc*/
    public ScriptEngineFactory getFactory() {
	return this.factory;
    }

    protected ScriptContext getPhpScriptContext() {
        Bindings namespace;
        ScriptContext scriptContext = new PhpScriptContext();
        
        namespace = createBindings();
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
 }
