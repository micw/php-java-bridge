/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

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

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;

/**
 * A custom context factory, creates a ContextFactory for JSR223 contexts.
 * This context factory does not wait for the continuation to terminate. 
 * 
 * Use the InvocablePhpServletContextFactory if you need to wait for
 * the end of the php to java communication.
 * 
 * @see InvocablePhpServletContextFactory
 * @author jostb
 *
 */
class PhpServletContextFactory extends php.java.servlet.ServletContextFactory {

    protected PhpServletContextFactory(Servlet servlet, ServletContext ctx,
			HttpServletRequest proxy, HttpServletRequest req,
			HttpServletResponse res) {
		super(servlet, ctx, proxy, req, res);
	}
    /**
     * Add the PhpScriptContext
     * @param context
     * @return The ContextFactory.
     */
    public static IContextFactory addNew(IContext context, Servlet servlet, 
		    ServletContext ctx, HttpServletRequest req, HttpServletResponse res) {
	    PhpServletContextFactory kontext = new PhpServletContextFactory(servlet, ctx, req, req, res);
	    kontext.setContext(context);
	    return kontext;
    }
}
