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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import php.java.bridge.PhpProcedure;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;

/**
 * This class implements the ScriptEngine.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = (new ScriptEngineManager()).getEngineByName("php-invocable");<br>
 * e.eval(&lt;? function f() {return java_server_name();}?&gt;<br>
 * System.out.println(((Invocable)e).invokeFunction("f", new Object[]{}));<br>
 * e.eval((String)null);<br>
 * </code><br>
 */
public class InvocablePhpScriptEngine extends SimplePhpScriptEngine implements Invocable {
    private static boolean registeredHook = false;
    private static List engines = new LinkedList();
     
    /**
     * Create a new ScriptEngine with a default context.
     */
    public InvocablePhpScriptEngine() {
	super();
    }

    /**
     * Create a new ScriptEngine from a factory.
     * @param factory The factory
     * @see #getFactory()
     */
    public InvocablePhpScriptEngine(PhpScriptEngineFactory factory) {
        super(factory);
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object[])
     */
    public Object invoke(String methodName, Object[] args)
	throws ScriptException, NoSuchMethodException {
        
	if(scriptClosure==null) eval("<?php ?>");
	
	try {
	    return invoke(scriptClosure, methodName, args);
	} catch (php.java.bridge.Request.AbortException e) {
	    release ();
	    throw new ScriptException(e);
	}
    }
    public Object invokeFunction(String methodName, Object[] args)
	throws ScriptException, NoSuchMethodException {
	return invoke(methodName, args);
    }

    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object, java.lang.Object[])
     */
    public Object invoke(Object thiz, String methodName, Object[] args)
	throws ScriptException, NoSuchMethodException {
	PhpProcedure proc = (PhpProcedure)(Proxy.getInvocationHandler(thiz));
	try {
	    return proc.invoke(script, methodName, args);
	} catch (ScriptException e) {
	    throw e;
	} catch (NoSuchMethodException e) {
	    throw e;
	} catch (RuntimeException e) {
	    throw e; // don't wrap RuntimeException
	} catch (Error er) {
	    throw er;
	} catch (Throwable e) {
	    throw new PhpScriptException("Invocation threw exception ", e);
	}
    }
    /* (non-Javadoc)
     * @see javax.script.Invocable#call(java.lang.String, java.lang.Object, java.lang.Object[])
     */
    public Object invokeMethod(Object thiz, String methodName, Object[] args)
	throws ScriptException, NoSuchMethodException {
	return invoke(thiz, methodName, args);
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
      if(thiz==null) throw new NullPointerException("Object thiz cannot be null. Please check if the previous call to eval() reported any errors.");
      return ((PhpProcedureProxy)thiz).getNewFromInterface(clasz);
    }

    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        if(continuation != null) release();
  	if(reader==null) return null;
  	
  	setNewContextFactory();
        setName(name);

        IPhpScriptContext ctx = (IPhpScriptContext)getContext(); 
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(out);
        Reader localReader = null;
        char[] buf = new char[Util.BUF_SIZE];
        int c;

        try {
            /* header: <? require_once("http://localhost:<ourPort>/JavaBridge/java/Java.inc"); ?> */
            localReader = new StringReader("<?php require_once(\""+ctx.getContextString()+"/java/Java.inc\"); ?>");
            try { while((c=localReader.read(buf))>0) w.write(buf, 0, c);} catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not read header", e);}
            try { localReader.close(); } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not close header", e);}
    
            /* the script: */
            try { while((c=reader.read(buf))>0) w.write(buf, 0, c);} catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not read script", e);}
    
            /* get the default, top-level, closure and call it, to stop the script from terminating */
            localReader =  new StringReader("<?php java_context()->call(java_closure()); ?>");
            try { while((c=localReader.read(buf))>0) w.write(buf, 0, c);} catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not read footer", e);}
            try { localReader.close(); } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not read footer", e);}
            try { w.close(); } catch (IOException e) {/*ignore*/}
    
            /* now evaluate our script */
            localReader = new InputStreamReader(new ByteArrayInputStream(out.toByteArray()));
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not evaluate footer", e);}
    
            if (this.script!=null) {
        	/* get the proxy, either the one from the user script or our default proxy */
        	try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
        	handleRelease();
            } else {
        	throw this.scriptException = new PhpScriptException("Could not evaluate script, please check your php.ini file and see the error log for details.");
            }
        } finally {
            if(w!=null)  try { w.close(); } catch (IOException e) {/*ignore*/}
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}            
        }
       return null;
    }

    protected void handleRelease() {
        // make sure to properly release them upon System.exit().
        synchronized(engines) {
            if(!registeredHook) {
        	registeredHook = true;
        	try {
        	    Runtime.getRuntime().addShutdownHook(new Util.Thread() {
        		public void run() {
        		    if (engines==null) return;
        		    synchronized(engines) {
        			for(Iterator ii = engines.iterator(); ii.hasNext(); ii.remove()) {
        			    InvocablePhpScriptEngine e = (InvocablePhpScriptEngine) ii.next();
        			    e.releaseInternal();
        			}
        		    }
        		}});
        	} catch (SecurityException e) {/*ignore*/}
            }
            engines.add(this);
        }
    }
    private void releaseInternal() {
	super.release();
    }
    public void release() {
	synchronized(engines) {
	    releaseInternal();
	    engines.remove(this);
	}
    }
}