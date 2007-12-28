/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;

/**
 * This class implements the ScriptEngine.<p>
 *@see php.java.script.InvocablePhpScriptEngine
 *@see php.java.script.PhpScriptEngine
 *@author jostb
 */
abstract class SimplePhpScriptEngine extends AbstractScriptEngine {

	
    /**
     * The allocated script
     */
    protected PhpProcedureProxy script = null;
    protected Object scriptClosure = null;
    protected ScriptException scriptException = null;
    protected String name = null;
    
    /**
     * The continuation of the script
     */
    protected HttpProxy continuation = null;
    protected final HashMap processEnvironment = getProcessEnvironment();
    protected Map env = null;
    IContextFactory ctx = null;
    
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
	} catch (Exception e) {
	    defaultEnv.putAll(Util.COMMON_ENVIRONMENT);
	}
	return defaultEnv;
    }

    /**
     * Create a new ScriptEngine with a default context.
     */
    public SimplePhpScriptEngine() {
        initialize(); 	// initialize it here, otherwise we have to override all inherited methods.
        		// needed because parent (JDK1.6) initializes and uses a protected context field 
        		// instead of calling getContext() when appropriate.
    }

    /**
     * Create a new ScriptEngine from a factory.
     * @param factory The factory
     * @see #getFactory()
     */
    public SimplePhpScriptEngine(PhpScriptEngineFactory factory) {
        this();
        this.factory = factory;
    }

    /* Revert constructor chain. Call super(false); privateInit(); super.initialize(), 
     * see PhpFacesScriptEngine constructor and PhpScriptEngine() constructor. -- The jsr223 API is really odd ... */
    protected SimplePhpScriptEngine(boolean initialize) {
        if(initialize) initialize();
    }
    protected void setName(String name) {
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
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = PhpScriptContextFactory.addNew((IContext)context);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	/* the client should connect back to us */
	StringBuffer buf = new StringBuffer("h:");
	buf.append(Util.getHostAddress());
	buf.append(':');
	buf.append(context.getSocketName());
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",buf.toString());
    }

    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        /* PHP executes a script and then terminates immediately. Use the php-invocable ScriptEngine to hold a PHP script */
	if(continuation != null) throw new IllegalStateException("continuation is not null.");

	try {
            if(reader==null) return null;
      	
	    setNewContextFactory();
            setName(name);
    
            try { doEval(reader, context); } catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
        } finally {
            release();
        }
	return null;
    }
    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return eval(reader, context, String.valueOf(reader));
    }

    private void updateGlobalEnvironment(ScriptContext context) {
	Bindings bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
	if(bindings==null) return;
	for(Iterator ii = bindings.entrySet().iterator(); ii.hasNext(); ) {
	    Entry entry = (Entry) ii.next();
	    Object key = entry.getKey();
	    Object val = entry.getValue();
	    env.put(key, val);
	}
    }
    private final class HeaderParser extends Util.HeaderParser {
	private OutputStreamWriter writer;
	public HeaderParser(OutputStreamWriter writer) {
	    this.writer = writer;
	}
	public void parseHeader(String header) {
	    if(header==null) return;
	    int idx = header.indexOf(':');
	    if(idx==-1) return;
	    String key = header.substring(0, idx).trim().toLowerCase();
	    String val = header.substring(idx+1).trim();
	    addHeader(key, val);
	}
	protected void addHeader(String key, String val) {
	    if(val!=null && key.equals("content-type")) {
		int idx = val.indexOf(';');
		if(idx==-1) return;
		String enc = val.substring(idx+1).trim();
		idx = enc.indexOf('=');
		if(idx==-1) return;
		enc=enc.substring(idx+1);
		writer.setEncoding(enc);
	    }
	}
    }
    protected HttpProxy getContinuation(Reader reader, ScriptContext context) {
    	Util.HeaderParser headerParser = Util.DEFAULT_HEADER_PARSER; // ignore encoding, we pass everything directly
	IPhpScriptContext phpScriptContext = (IPhpScriptContext)context;
    	updateGlobalEnvironment(context);
    	OutputStream out =((PhpScriptWriter)(context.getWriter())).getOutputStream();
    	OutputStream err =  ((PhpScriptWriter)(context.getErrorWriter())).getOutputStream();

	/*
	 * encode according to content-type charset
	 */
    	if(out instanceof OutputStreamWriter)
    	    headerParser = new HeaderParser((OutputStreamWriter)out);

    	HttpProxy kont = new HttpProxy(reader, env, out,  err, headerParser); 
     	phpScriptContext.setContinuation(kont);
	return kont;
    }

    /*
     * Obtain a PHP instance for url.
     */
    protected PhpProcedureProxy doEval(Reader reader, ScriptContext context) throws Exception {
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
      	if(script==null) return eval((Reader)null, context, null);
      	
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
 * @throws InterruptedException 
     */
    public void release() {
	if(continuation != null) {
	    try {
		continuation.release();
	        ctx.waitFor();
	    } catch (InterruptedException e) {
		    return;
	    }
	    ctx.removeOrphaned(); ctx = null;
	    continuation = null;
	    script = null;
	    scriptClosure = null;
	    scriptException = null;
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
