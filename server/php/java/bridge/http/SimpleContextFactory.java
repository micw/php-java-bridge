/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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

import php.java.bridge.ISession;
import php.java.bridge.JavaBridge;
import php.java.bridge.Util;
import php.java.bridge.SimpleJavaBridgeClassLoader;


/**
 * Base of a set of visitors which can extend the standard ContextFactory.
 *  
 * @see php.java.servlet.ServletContextFactory
 * @see php.java.script.PhpScriptContextFactory
 */
public class SimpleContextFactory implements IContextFactoryVisitor {
    
    /**
     * The session object
     */
    protected ISession session;
    
    /**
     * The visited ContextFactory
     */
    protected ContextFactory visited;

    /**
     * The jsr223 context or the emulated jsr223 context.
     */
    protected IContext context;
    
    private boolean isInitialized = false;
    private boolean isValid = true;
    
    protected SimpleContextFactory(String webContext) {
  	visited = new ContextFactory(webContext);
  	visited.accept(this);
    	setClassLoader(Util.getContextClassLoader());
    }
    
    public void recycle(String id) throws SecurityException {
        visited.recycle(id);
    }

    public void destroy() {
        visited.destroy();
        session = null;
    }
    
    public synchronized void invalidate() {
	    isValid = false;
	    notifyAll(); // the servlet and the http proxy, see SimplePhpScriptEngine.release
    }
    public synchronized void initialize () {
	isInitialized = true;
    }
    /**
     * Wait for the context factory to finish. 
     */
    public synchronized void waitForInitializedContext() throws InterruptedException {
	if(Util.logLevel>4) Util.logDebug("contextfactory: servlet is waiting for ContextFactory " +System.identityHashCode(this));
	if (isInitialized && isValid) wait();
	if(Util.logLevel>4) Util.logDebug("contextfactory: servlet done waiting for ContextFactory " +System.identityHashCode(this));
    }
    /**
     * Wait for the context factory to finish. 
     */
    public synchronized void waitFor(long timeout) throws InterruptedException {
	if(Util.logLevel>4) Util.logDebug("contextfactory: servlet is waiting for ContextFactory " +System.identityHashCode(this) + " for " +timeout+" ms");
	if (isValid) wait(timeout);
	if(Util.logLevel>4) Util.logDebug("contextfactory: servlet done waiting for ContextFactory " +System.identityHashCode(this)+ " for " +timeout+" ms");
    }    
    public String getId() { 
        return visited.getId();
    }
    public String toString() {
	return "ContextFactory: " + visited + ", SimpleContextFactory: " +getClass() + ", current loader: " + loader;
    }
    /**
     * Create a new context. The default implementation
     * creates a dummy context which emulates the JSR223 context.
     * @return The context.
     */
    protected IContext createContext() {
      return new Context();
  }
    public IContext getContext() {
	if(context==null) setContext(createContext());
        return context;
    }

    public boolean isNew () {
	return visited.isNew();
    }
    public JavaBridge getBridge() {
        return visited.getBridge();
    }
    public void visit(ContextFactory visited) {
        this.visited=visited;
    }
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
	if(session != null) return session;
	return session = visited.getSimpleSession(name, clientIsNew, timeout);
    }
    public void setContext(IContext context) {
        this.context = context;
        this.context.setAttribute(IContext.JAVA_BRIDGE, getBridge(), IContext.ENGINE_SCOPE);
    }
    public void release() {
        visited.release();
    }

    /**
     * Called by recycle at the end of the script
     */
    public void recycle() {
	session = null;
    }

    private ClassLoader loader;
    /**
     * Return the current class loader.
     * @return the current DynamicJavaBridgeClassLoader
     */
    public ClassLoader getClassLoader() {
	return loader;
    }

    /**
     * Return the JavaBridgeClassLoader, which wraps the
     * DynamicJavaBridgeClassLoader
     */
    public SimpleJavaBridgeClassLoader getJavaBridgeClassLoader() {
	return visited.getJavaBridgeClassLoader();
    }

    /**
     * Set the current class loader
     * @param loader The DynamicJavaBridgeClassLoader
     */
    public void setClassLoader(ClassLoader loader) {
	if(loader==null) 
	    throw new NullPointerException("loader");
	this.loader = loader;
    }
}
