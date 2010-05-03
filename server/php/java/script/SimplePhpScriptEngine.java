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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
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

import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.ThreadPool;
import php.java.bridge.Util;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.bridge.http.WriterOutputStream;

/**
 * This class implements the ScriptEngine.<p>
 *@see php.java.script.InvocablePhpScriptEngine
 *@see php.java.script.PhpScriptEngine
 */
abstract class SimplePhpScriptEngine extends AbstractScriptEngine {

	
    /**
     * The allocated script
     */
    protected Object script = null;
    protected Object scriptClosure = null;
    protected String name = null;
    
    /**
     * The continuation of the script
     */
    protected HttpProxy continuation = null;
    protected Map env = null;
    protected IContextFactory ctx = null;
    
    private ScriptEngineFactory factory = null;
    protected ResultProxy resultProxy;

    protected void initialize() {
	setContext(getPhpScriptContext());
    }

    static HashMap getProcessEnvironment() {
	return Util.COMMON_ENVIRONMENT;
    }

    public static final ThreadPool pool = getThreadPool();
    private static synchronized ThreadPool getThreadPool() {
	return new ThreadPool("JavaBridgeHttpProxy", Integer.parseInt(Util.THREAD_POOL_MAX_SIZE)) {
	    protected Delegate createDelegate(String name) {
		Delegate d = super.createDelegate(name);
		d.setDaemon(true);
		return d;
	    }
	};
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

    protected void setName(String name) {
        int length = name.length();
        if(length>160) length=160;
        name = name.substring(0, length);
        this.name = name;
    }

    /**
     * Set the context id (X_JAVABRIDGE_CONTEXT) and the override flag (X_JAVABRIDGE_OVERRIDE_HOSTS) into env
     * @param env the environment which will be passed to PHP
     */
    protected void setStandardEnvironmentValues (Map env) {
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	
	/* the client should connect back to us */
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", ctx.getRedirectString());
    }
    protected void addNewContextFactory() {
	ctx = PhpScriptContextFactory.addNew((IContext)getContext());
    }
    protected ContextServer getContextServer() {
	return JavaBridgeRunner.contextServer;
    }
    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
	env = (Map) getProcessEnvironment().clone();

	addNewContextFactory();
	
    	// short path S1: no PUT request
	    ContextServer contextServer = getContextServer();
    		AbstractChannelName channelName = contextServer.getChannelName(ctx);
    		if (channelName != null) {
    		    env.put("X_JAVABRIDGE_REDIRECT", channelName.getName());
    		    ctx.getBridge();
    		    contextServer.start(channelName, Util.getLogger());
    		}
	
    	setStandardEnvironmentValues(env);
    }

    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        if(reader == null) { release(); return null; }

	/* PHP executes a script and then terminates immediately. Use the php-invocable ScriptEngine to hold a PHP script */
	if(continuation != null) throw new IllegalStateException("continuation is not null.");

	try {
	    setNewContextFactory();
            setName(name);
    
            try { doEval(reader, context); } catch (Exception e) {
        	Util.printStackTrace(e);
        	throw new PhpScriptException("Could not evaluate script", e);
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
	private WriterOutputStream writer;
	public HeaderParser(WriterOutputStream writer) {
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
	public void addHeader(String key, String val) {
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
    	if(out instanceof WriterOutputStream)
    	    headerParser = new HeaderParser((WriterOutputStream)out);

    	HttpProxy kont = new HttpProxy(reader, env, out,  err, headerParser, resultProxy = new ResultProxy(this), Util.getLogger()); 
     	phpScriptContext.setContinuation(kont);
	return kont;
    }

    /*
     * Obtain a PHP instance for url.
     */
    protected Object doEval(Reader reader, ScriptContext context) throws Exception {
    	continuation = getContinuation(reader, context);
     	pool.start(continuation);
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
     */
    public void release() {
	if(continuation != null) {
	    try {
		continuation.release();
		ctx.releaseManaged();
	    } catch (InterruptedException e) {
		    return;
	    }
	    ctx = null;
	    
	    continuation = null;
	    script = null;
	    scriptClosure = null;
	    
	    try {getContext().getWriter().flush();} catch (Exception e) {Util.printStackTrace(e);}
	    try {getContext().getErrorWriter().flush();} catch (Exception e) {Util.printStackTrace(e);}
	}
    }

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#createBindings()
     */
    /** {@inheritDoc} */
    public Bindings createBindings() {
        return new SimpleBindings();
    } 
    /**
     * Release the script engine.
     * @throws IOException 
     */
    public void close() throws IOException {
	release();
    }
}
