/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.script.ScriptEngine;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.script.CloseableScriptEngineDecorator;
import php.java.script.PhpScriptEngine;
import php.java.servlet.ContextLoaderListener;


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

class EngineFactoryHelper {
    public static ScriptEngine newCloseablePhpServletScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, File compilerOutputFile, String protocol, int port) throws MalformedURLException {
	return new CloseableScriptEngineDecorator(new PhpServletScriptEngine(servlet, ctx, req, res, compilerOutputFile, protocol, port));
    }
    public static ScriptEngine newCloseableInvocablePhpServletRemoteHttpServerScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, URI uri, File compilerOutputFile, String localName) throws MalformedURLException, URISyntaxException {
	return new CloseableScriptEngineDecorator(new InvocablePhpServletRemoteHttpServerScriptEngine(servlet, ctx, req, res, uri, compilerOutputFile, localName));
    }
    public static List getManagedEngineList (ServletContext ctx) {
	return (List) ctx.getAttribute(ContextLoaderListener.ENGINES);
    }
    public static void destroyEngineFactory () {
	PhpScriptEngine.pool.destroy();
    }
 }