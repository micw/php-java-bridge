/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

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

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.script.IPhpScriptContext;
import php.java.servlet.ServletContextFactory;

/**
 * A custom ContextFactory, manages a custom ScriptContext.
 * @author jostb
 *
 */
public class PhpFacesScriptContextFactory extends php.java.servlet.ServletContextFactory {
     protected IPhpScriptContext jsrContext = null;
    /**
     * Create a new ContextFactory
     * @param context The ScriptContext
     * @param kontext The ServletContext
     * @param req The ServletRequest
     * @param res The ServletResponse
     * @return The ContextFactory
     */
    public static ServletContextFactory addNew(IPhpScriptContext context, ServletContext kontext, HttpServletRequest req, HttpServletResponse res) {
	PhpFacesScriptContextFactory ctx = new PhpFacesScriptContextFactory(context, kontext, req, res);
	return ctx;
    }
    protected PhpFacesScriptContextFactory(IPhpScriptContext context, ServletContext ctx, HttpServletRequest req, HttpServletResponse res) { 
	super(ctx, req, req, res);
	this.jsrContext = context; 
    }
    /**
     * Returns the JSR223 ScriptContext.
     * @return The context
     */
    public Object createContext() {
        return jsrContext;
    }
}
