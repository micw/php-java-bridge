/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import php.java.script.servlet.EngineFactory;

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
 * &lt;listener&gt;
 * &nbsp;&nbsp;&lt;listener-class&gt;php.java.servlet.RequestListener&lt;/listener-class&gt;
 *&lt;/listener&gt;
 * </code>
 * </blockquote>
 * @see php.java.script.servlet.EngineFactory
 */
public class RequestListener implements javax.servlet.ServletRequestListener {
    /** The key used to store the engine list in the servlet request */
    public static final String ROOT_ENGINES_COLLECTION_ATTRIBUTE = RequestListener.class.getName()+".ROOT";;
    /**{@inheritDoc}*/
    public void requestDestroyed(ServletRequestEvent event) {
	HttpServletRequest req = (HttpServletRequest) event.getServletRequest();
	ServletContext ctx = event.getServletContext();
	List list = (List) req.getAttribute(ROOT_ENGINES_COLLECTION_ATTRIBUTE);
	if (list == null) return;
	
	EngineFactory factory = (EngineFactory) ctx.getAttribute(php.java.script.servlet.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE);
	req.removeAttribute(ROOT_ENGINES_COLLECTION_ATTRIBUTE);
	factory.releaseScriptEngines(list);
    }
    /**{@inheritDoc}*/
    public void requestInitialized(ServletRequestEvent event) {
	HttpServletRequest req = (HttpServletRequest) event.getServletRequest();
	String isSubRequest = req.getHeader("X_JAVABRIDGE_CONTEXT");
	if(isSubRequest==null)
	    req.setAttribute(ROOT_ENGINES_COLLECTION_ATTRIBUTE, new ArrayList());
    }
}
