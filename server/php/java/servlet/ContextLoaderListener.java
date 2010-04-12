/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import php.java.bridge.ILogger;
import php.java.bridge.Util;
import php.java.bridge.http.ContextServer;

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
 * Register a PHP JSR 223 EngineFactory when the web context starts. Used when the web application
 * WEB-INF/web.xml contains a listener attribute:
 * <blockquote>
 * <code>
 * &lt;listener&gt;
 * &nbsp;&nbsp;&lt;listener-class&gt;php.java.servlet.ContextLoaderListener&lt;/listener-class&gt;
 *&lt;/listener&gt;
 * </code>
 * </blockquote>
 * @see php.java.script.servlet.EngineFactory
 */
public class ContextLoaderListener implements javax.servlet.ServletContextListener {
    /** The key used to store the closeables list in the servlet context, must be destroyed before the engines */
    public static final String CLOSEABLES = ContextLoaderListener.class.getName()+".CLOSEABLES";
    /** The key used to store the jsr 223 engines list in the servlet context */
    public static final String ENGINES = ContextLoaderListener.class.getName()+".ENGINES";
    /** The key used to store the jsr 223 engines list in the servlet context */
    public static final String LOGGER = ContextLoaderListener.class.getName()+".LOGGER";

    protected ILogger logger;

    /** Only for internal use 
     * @param ctx The servlet context 
     */
    public static void destroyCloseables(ServletContext ctx) {
	List list = (List) ctx.getAttribute(CLOSEABLES);
	if (list == null) return;
	
	try {
	    for (Iterator ii = list.iterator(); ii.hasNext(); ) {
		Object c = ii.next();
		try {
		    Method close = c.getClass().getMethod("close", Util.ZERO_PARAM);
		    close.invoke(c, Util.ZERO_ARG);
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	} catch (Throwable t) {
	    t.printStackTrace();
	} finally {
	    ctx.removeAttribute(CLOSEABLES);
	}
    }

    /** Only for internal use 
     * @param ctx The servlet context
     * */
    public static void destroyScriptEngines (ServletContext ctx) {
	try {
	    php.java.script.servlet.EngineFactory factory = null;
	    try {
		factory = (php.java.script.servlet.EngineFactory)ctx.getAttribute(php.java.script.servlet.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE);
	    } catch (NoClassDefFoundError e) { /* ignore */ }
	    if (factory == null) return;
	
	    List list = (List) ctx.getAttribute(ENGINES);
	    if (list != null)
		factory.releaseScriptEngines(list);
	    factory.destroy();
	} finally {
	    ctx.removeAttribute(ENGINES);
	    ctx.removeAttribute(LOGGER);
	}
    }
    /**{@inheritDoc}*/  
    public void contextDestroyed(ServletContextEvent event) {
	ServletContext ctx = event.getServletContext();
	try {
	    destroyCloseables(ctx);
	    destroyScriptEngines(ctx);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	ctx.removeAttribute(php.java.script.servlet.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE);
	
	ContextServer contextServer = (ContextServer)ctx.getAttribute(ContextServer.ROOT_CONTEXT_SERVER_ATTRIBUTE);
    	if (contextServer != null) contextServer.destroy();
	ctx.removeAttribute(ContextServer.ROOT_CONTEXT_SERVER_ATTRIBUTE);
	
	ctx.removeAttribute(ServletUtil.HOST_ADDR_ATTRIBUTE);
	
    }
    /**{@inheritDoc}*/  
    public void contextInitialized(ServletContextEvent event) {
	try {
	    Class clazz = Class.forName("php.java.script.servlet.EngineFactory",true,Thread.currentThread().getContextClassLoader());
	    ServletContext ctx = event.getServletContext();
	    ctx.setAttribute(php.java.script.servlet.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE, clazz.newInstance());
	    ctx.setAttribute(ENGINES, Collections.synchronizedList(new ArrayList()));
	    ctx.setAttribute(CLOSEABLES, new LinkedList());
	
	    boolean isJBoss = false;
	    String name = ctx.getServerInfo();
	    if (name != null && (name.startsWith("JBoss")))    isJBoss    = true;
	    
	    logger = new Util.Logger(!isJBoss, new Logger(ctx));
	    ctx.setAttribute(LOGGER, logger);

	    
	    boolean promiscuous = false;
	    try {
		String value = ctx.getInitParameter("promiscuous");
		if(value==null) value="";
		value = value.trim();
		value = value.toLowerCase();
		    
		if(value.equals("on") || value.equals("true")) promiscuous=true;
	    } catch (Exception t) {t.printStackTrace();}
	    ServletUtil.getContextServer(ctx, promiscuous);
	    
	    ctx.setAttribute(ServletUtil.HOST_ADDR_ATTRIBUTE, Util.getHostAddress(promiscuous));

	} catch (InstantiationException e) {
	    e.printStackTrace();
        } catch (IllegalAccessException e) {
	    e.printStackTrace();
        } catch (ClassNotFoundException e) {
	    e.printStackTrace();
        } catch (NoClassDefFoundError e) {
	    e.printStackTrace();
        }
    }
}
