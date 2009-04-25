/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;


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

/**
 * Create JSR 223 script engines from a servlet context.
 * @see php.java.servlet.ContextLoaderListener
 * @see php.java.script.servlet.PhpServletScriptEngine
 * @see php.java.script.servlet.InvocablePhpServletRemoteHttpServerScriptEngine
 *
 */
public final class EngineFactory {
    /** The key used to store the factory in the servlet context */
    public static final String ROOT_ENGINE_FACTORY_ATTRIBUTE = EngineFactory.class.getName()+".ROOT";
    private boolean hasCloseable;
    /**
     * Create a new EngineFactory
     */
    public EngineFactory () {
	try {
	    Class.forName("java.io.Closeable");
	    hasCloseable = true;
	} catch (ClassNotFoundException e) {
	    hasCloseable = false;
	}
    }
    private Object getScriptEngine(Servlet servlet, 
		     ServletContext ctx, 
		     HttpServletRequest req, 
		     HttpServletResponse res) throws MalformedURLException {
	return hasCloseable ? 
	    EngineFactoryHelper.newCloseablePhpServletScriptEngine(servlet, ctx, req, res, req.getScheme(), req.getServerPort()):
	    new PhpServletScriptEngine(servlet, ctx, req, res, req.getScheme(), req.getServerPort());
    }
    private Object getInvocableScriptEngine(Servlet servlet, 
	     ServletContext ctx, 
	     HttpServletRequest req, 
	     HttpServletResponse res, 
	     URI uri,
	     String localName) throws MalformedURLException, URISyntaxException {
	return hasCloseable ?
		EngineFactoryHelper.newCloseableInvocablePhpServletRemoteHttpServerScriptEngine(servlet, ctx, req, res, uri, localName) :
		new InvocablePhpServletRemoteHttpServerScriptEngine(servlet, ctx, req, res, uri, localName);
   }

    /** 
     * Get an engine factory from the servlet context
     * @param ctx The servlet context
     * @return the factory or null
     */
    static EngineFactory getEngineFactory(ServletContext ctx) {
	EngineFactory attr = (EngineFactory) 
	    ctx.getAttribute(php.java.script.servlet.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE);
	return attr;
    }
    /**
     * Get an engine factory from the servlet context
     * @param ctx The servlet context
     * @return the factory
     * @throws IllegalStateException
     */
    static EngineFactory getRequiredEngineFactory(ServletContext ctx) throws IllegalStateException {
	EngineFactory attr = getEngineFactory (ctx);
	if (attr==null) 
	    throw new IllegalStateException("No EngineFactory found. Have you registered a listener?");
	return attr;
    }

    /**
     * Get a PHP JSR 223 ScriptEngine from the servlet context.
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * private static final Reader HELLO_SCRIPT_READER = EngineFactory.createPhpScriptReader("&lt;?php echo 'Hello!'; ?&gt;");<br>
     * Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_SCRIPT_READER);<br>
     * ScriptEngine scriptEngine = EngineFactory.getPhpScriptEngine(this, application, request, response);<br>
     * scriptEngine.eval(reader);<br>
     * reader.close();<br>
     * </code>
     * </blockquote>
     * @param servlet the servlet
     * @param ctx the servlet context
     * @param req the request
     * @param res the response
     * @return the PHP JSR 223 ScriptEngine
     * @throws Exception 
     */
    public static javax.script.ScriptEngine getPhpScriptEngine (final Servlet servlet, 
								final ServletContext ctx, 
								final HttpServletRequest req, 
								final HttpServletResponse res) throws 
								    Exception {
	return (ScriptEngine) AccessController.doPrivileged(new PrivilegedExceptionAction(){ 
	    public Object run() throws Exception {
		return (javax.script.ScriptEngine)EngineFactory.getRequiredEngineFactory(ctx).getScriptEngine(servlet, ctx, req, res);
	    }
	});
    }
    /**
     * Get a PHP JSR 223 ScriptEngine, which implements the Invocable interface, from a HTTP server running on the local or a remote host.
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * ScriptEngine scriptEngine = EngineFactory.getInvocablePhpScriptEngine(this, application, request, response, new URI("http://127.0.0.1:80/JavaBridge/java/JavaProxy.php"), "thisHostName");<br>
     * ...<br>
     * Invocable invocableEngine = (Invocable)scriptEngine;<br>
     * invocableEngine.invoceFunction("phpinfo", new Object[]{});<br>
     * ...<br>
     * scriptEngine.eval ((Reader)null);<br>
     * </code>
     * </blockquote>
     * Note: When connecting to a remote host, the <code>WEB-INF/web.xml promiscuous</code> option must be set.<br>
     * @param servlet the servlet
     * @param ctx the servlet context
     * @param req the request
     * @param res the response
     * @param uri the URI of the remote PHP script engine. The localName is used by the remote script engine to connect back to the current host.
     * @param localName the official STATIC(!) server name or ip address of this host (in case there's an IP based load balancer in between).
     * @return the invocable PHP JSR 223 ScriptEngine
     * @throws Exception 
     */
    public static javax.script.ScriptEngine getInvocablePhpScriptEngine (final Servlet servlet, 
									 final ServletContext ctx, 
									 final HttpServletRequest req, 
									 final HttpServletResponse res,
									 final URI uri,
									 final String localName) throws 
									     Exception {
	return (ScriptEngine) AccessController.doPrivileged(new PrivilegedExceptionAction(){ 
	    public Object run() throws Exception {
	    return (javax.script.ScriptEngine)EngineFactory.getRequiredEngineFactory(ctx).getInvocableScriptEngine(servlet, ctx, req, res, uri, localName);
	    }
	});
    }
    /**
     * Get a PHP JSR 223 ScriptEngine, which implements the Invocable interface, from a HTTP server running on the local or a remote host.
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * ScriptEngine scriptEngine = EngineFactory.getInvocablePhpScriptEngine(this, application, request, response, new URI("http://127.0.0.1:80/JavaBridge/java/JavaProxy.php"));<br>
     * ...<br>
     * Invocable invocableEngine = (Invocable)scriptEngine;<br>
     * invocableEngine.invoceFunction("phpinfo", new Object[]{});<br>
     * ...<br>
     * scriptEngine.eval ((Reader)null);<br>
     * </code>
     * </blockquote>
     * Note: When connecting to a remote host, the <code>WEB-INF/web.xml promiscuous</code> option must be set.<br>
     * @param servlet the servlet
     * @param ctx the servlet context
     * @param req the request
     * @param res the response
     * @param uri the URI of the remote PHP script engine, there must not be an IP-based load balancer in between
     * @return the invocable PHP JSR 223 ScriptEngine
     * @throws Exception 
     */
    public static javax.script.ScriptEngine getInvocablePhpScriptEngine (final Servlet servlet, 
									 final ServletContext ctx, 
									 final HttpServletRequest req, 
									 final HttpServletResponse res,
									 final URI uri) throws 
									     Exception {
	return getInvocablePhpScriptEngine(servlet, ctx, req, res, uri, req.getLocalName());
    }
    
    /**
     * Create a Reader from a given PHP script file. This procedure can be used to create
     * a reader from a cached script
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * private static final Reader HELLO_READER = EngineFactory.createPhpScriptReader("&lt;?php echo 'Hello!'; ?&gt;");<br>
     * Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_READER);<br>
     * scriptEngine.eval (reader);<br>
     * reader.close();<br>
     * ...<br>
     * </code>
     * </blockquote>
     * @param script the php script.
     * @return A reference to the cached PHP script
     */
    public static Reader createPhpScriptReader (String script) {
	return new ScriptReader(script);
    }
    /**
     * Create a Reader from a given PHP script file. This procedure can be used to create
     * a reader from a cached script
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * private static final Reader HELLO_READER = EngineFactory.createPhpScriptReader(new StringReader("&lt;?php echo 'Hello!'; ?&gt;"));<br>
     * Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_READER);<br>
     * scriptEngine.eval (reader);<br>
     * reader.close();<br>
     * ...<br>
     * </code>
     * </blockquote>
     * @param script the script reader, will be closed automatically by the bridge.
     * @return A reference to the cached PHP script
     */
    public static Reader createPhpScriptReader (Reader script) {
	return new ScriptReader(script);
    }

    private static final class OneShotReader extends FilterReader implements IScriptReader {

	protected OneShotReader(Reader in) {
	    super(in);
        }

	public boolean isClosed() {
	    try {
		in.ready();
	    } catch (IOException e) {
		return true;
	    }
	    return false;
        }
	
    }

    /**
     * Create a Reader from a given PHP script file. This procedure can be used to create
     * a reader from a cached script
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * private static final Reader HELLO_READER = EngineFactory.createPhpScriptReader("&lt;?php echo 'Hello!'; ?&gt;");<br>
     * Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_READER);<br>
     * scriptEngine.eval (reader);<br>
     * reader.close();<br>
     * ...<br>
     * </code>
     * </blockquote>
     * @param path the file containing the cached script
     * @param reader the reader, will be closed automatically by the bridge.
     * @return A reference to the cached PHP script
     */
    public static Reader createPhpScriptFileReader (final String path, final Reader reader) {
	return (Reader) AccessController.doPrivileged(new PrivilegedAction(){ 
	    public Object run() {
        	try {
        	    if (reader instanceof ScriptReader) 
        		return new ScriptFileReader(path, (ScriptReader)reader);
        	    else
        		return new ScriptFileReader(path, new OneShotReader(reader));
                } catch (IOException e) {
        	    Util.printStackTrace(e);
                }
        	return null;
	    }
	});
    }
    /**
     * Create a Reader from a given PHP script file. This procedure can be used to create
     * a reader from a cached script
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * private static final Reader HELLO_READER = EngineFactory.createPhpScriptReader("&lt;?php echo 'Hello!'; ?&gt;");<br>
     * Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_READER);<br>
     * scriptEngine.eval (reader);<br>
     * reader.close();<br>
     * ...<br>
     * </code>
     * </blockquote>
     * @param path the file containing the cached script
     * @param reader the reader, will be closed automatically by the bridge.
     * @return A reference to the cached PHP script
     */
    public static Reader createPhpScriptFileReader (final String path, final ScriptReader reader) {
	return (Reader) AccessController.doPrivileged(new PrivilegedAction(){ 
	    public Object run() {
        	try {
        	    return new ScriptFileReader(path, reader);
                } catch (IOException e) {
        	    Util.printStackTrace(e);
                }
        	return null;
	    }
	});
    }
    /**
     * Create a Reader from a given PHP script file. This procedure can be used to create
     * a reader from a cached script
     *
     * Example:<br>
     * <blockquote>
     * <code>
     * Reader reader = EngineFactory.createPhpScriptFileReader("/sessionSharing.php");<br>
     * scriptEngine.eval (reader);<br>
     * reader.close();<br>
     * ...<br>
     * </code>
     * </blockquote>
     * @param path the file containing the script
     * @return A reader for the script
     */
    public static Reader createPhpScriptFileReader (final String path) {
	return (Reader) AccessController.doPrivileged(new PrivilegedAction(){ 
	    public Object run() {
        	try {
        	    return new ScriptFileReader(path);
                } catch (IOException e) {
        	    Util.printStackTrace(e);
                }
        	return null;
	    }
	});
    }
    /**
     * Only for internal use.<br>
     * Release all managed script engines. Will be called automatically during shutdown,
     * @param list the list of script engines 
     */
    public void releaseScriptEngines(final List list) {
	AccessController.doPrivileged(new PrivilegedAction(){ 
	    public Object run() {
        	for (Iterator ii=list.iterator(); ii.hasNext(); ) {
        	    InvocablePhpServletLocalHttpServerScriptEngine engine = (InvocablePhpServletLocalHttpServerScriptEngine) ii.next();
        	    engine.release();
        	}
        	list.clear();
        	return null;
	    }
	});
    }
    /**
     * Manage a script engine
     * @param ctx the servlet context
     * @param engine the engine to manage
     * @throws ScriptException 
     * @see #releaseScriptEngines(List)
     */
    static void addManaged(ServletContext ctx,
		InvocablePhpServletLocalHttpServerScriptEngine engine) throws ScriptException {
	try {
	    addManagedInternal(ctx, engine);
	} catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            throw (ScriptException) e.getCause();
	}
    }
    private static void addManagedInternal(final ServletContext ctx,
	final InvocablePhpServletLocalHttpServerScriptEngine engine) throws PrivilegedActionException {
	AccessController.doPrivileged(new PrivilegedExceptionAction() { 
	    public Object run() throws Exception {
		
		List list = null;
		try {
		    list = EngineFactoryHelper.getManagedEngineList(ctx);
		} catch (NoClassDefFoundError e) { /**ignore for jdk 1.4*/ }
		
		if (list!=null) {
		    list.add(engine);
		    
		    // check
		    engine.reserveContinuation();
		}
		return null;
	    }
	});
    }
}
