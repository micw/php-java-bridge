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
import java.io.FileWriter;
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
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.ThreadPool;
import php.java.bridge.Util;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HeaderParser;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.bridge.http.WriterOutputStream;

/**
 * This class implements the ScriptEngine.<p>
 *@see php.java.script.InvocablePhpScriptEngine
 *@see php.java.script.PhpScriptEngine
 */
abstract class SimplePhpScriptEngine extends AbstractScriptEngine implements IPhpScriptEngine, Compilable, java.io.FileFilter {

	
    /**
     * The allocated script
     */
    protected Object script = null;
    protected Object scriptClosure = null;
    protected String name = null;
    
    /**
     * The continuation of the script
     */
    protected Continuation continuation = null;
    protected Map env = null;
    protected IContextFactory ctx = null;
    
    private ScriptEngineFactory factory = null;
    protected ResultProxy resultProxy;
    protected File compilerOutputFile;

    protected void initialize() {
	setContext(getPhpScriptContext());
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

    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return evalPhp(reader, context);
    }
    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.io.Reader, javax.script.ScriptContext)
     */
    protected Object evalPhp(Reader reader, ScriptContext context) throws ScriptException {
        return doEvalPhp(reader, getContext(), String.valueOf(reader));
    }
    private void updateGlobalEnvironment(ScriptContext context) throws IOException {
	Bindings bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
	if(bindings==null) return;
	for(Iterator ii = bindings.entrySet().iterator(); ii.hasNext(); ) {
	    Entry entry = (Entry) ii.next();
	    Object key = entry.getKey();
	    Object val = entry.getValue();
	    env.put(key, val);
	}
	if (compilerOutputFile != null) env.put("SCRIPT_FILENAME", compilerOutputFile.getCanonicalPath());
    }
    private final class SimpleHeaderParser extends HeaderParser {
	private WriterOutputStream writer;
	public SimpleHeaderParser(WriterOutputStream writer) {
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
    protected Continuation getContinuation(Reader reader, ScriptContext context) throws IOException {
	HeaderParser headerParser = HeaderParser.DEFAULT_HEADER_PARSER; // ignore encoding, we pass everything directly
	IPhpScriptContext phpScriptContext = (IPhpScriptContext)context;
    	updateGlobalEnvironment(context);
    	OutputStream out =((PhpScriptWriter)(context.getWriter())).getOutputStream();
    	OutputStream err =  ((PhpScriptWriter)(context.getErrorWriter())).getOutputStream();

	/*
	 * encode according to content-type charset
	 */
    	if(out instanceof WriterOutputStream)
    	    headerParser = new SimpleHeaderParser((WriterOutputStream)out);

    	Continuation kont = phpScriptContext.createContinuation(reader, env, out,  err, headerParser, resultProxy = new ResultProxy(this), Util.getLogger());

    	phpScriptContext.setContinuation(kont);
	return kont;
    }
    /** Method called to evaluate a PHP file w/o compilation */
    protected abstract Object doEvalPhp(Reader reader, ScriptContext context, String name) throws ScriptException;
    protected abstract Object doEvalCompiledPhp(Reader reader, ScriptContext context, String name) throws ScriptException;

    protected abstract Reader getLocalReader(Reader reader) throws IOException;
    protected void doCompile(Reader reader, ScriptContext context) throws IOException {
  	setNewContextFactory();
  	
	FileWriter writer = new FileWriter(this.compilerOutputFile);
	char[] buf = new char[Util.BUF_SIZE];
	Reader localReader = getLocalReader(reader);
	try {
		int c;
		while((c = localReader.read(buf))>0) 
		    writer.write(buf, 0, c);
		writer.close();
	} finally {
	    localReader.close();
	}
    }
    
    /*
     * Obtain a PHP instance for url.
     */
    final protected Object doEval(Reader reader, ScriptContext context) throws Exception {
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
      	if(script==null) return doEvalPhp((Reader)null, context, null);
      	
	script = script.trim();
	Reader localReader = new StringReader(script);
	try {
	    return eval(localReader, context);
	} finally {
	    try { localReader.close(); } catch (IOException e) {/*ignore*/}
	}
    }

    /**@inheritDoc*/
    public ScriptEngineFactory getFactory() {
	return this.factory;
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

    /** {@inheritDoc} */
    public CompiledScript compile(String script) throws ScriptException {
	Reader reader = new StringReader(script);
	try {
	    return compile(reader);
	} finally {
	    try {
	        reader.close();
            } catch (IOException e) {
	        Util.printStackTrace(e);
            }
	}
    }
    private static final Reader DUMMY_READER = new Reader() {
	/** {@inheritDoc} */
        public void close() throws IOException {
            throw new IllegalStateException("use compiled file");
        }
	/** {@inheritDoc} */
        public int read(char[] cbuf, int off, int len)
                throws IOException {
            throw new IllegalStateException("use compiled file");
       }};
       
    /** {@inheritDoc} */
    public CompiledScript compile(final Reader reader) throws ScriptException {
	if (this.compilerOutputFile==null) throw new IllegalStateException("No output file given");
	try {
	    doCompile(reader, getContext());
        } catch (IOException e) {
            throw new ScriptException(e);
        }
	return new CompiledScript() {
	    /** {@inheritDoc} */
	    public Object eval(final ScriptContext context) throws ScriptException {
		setContext(new PhpCompiledScriptContextDecorator((IPhpScriptContext)getContext()));
		try {
	            return SimplePhpScriptEngine.this.doEvalCompiledPhp(DUMMY_READER, getContext(), null);
                } catch (Exception e) {
                    throw new ScriptException(e);
                }
	    }
	    /** {@inheritDoc} */
	    public ScriptEngine getEngine() {
		return SimplePhpScriptEngine.this;
	    }
	};
    }
    /** {@inheritDoc} */
    public boolean accept(File outputFile) {
	this.compilerOutputFile = outputFile;
	return true;
    }
}
