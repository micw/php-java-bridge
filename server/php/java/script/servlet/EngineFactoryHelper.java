/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.servlet.CGIServlet;
import php.java.servlet.RequestListener;


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
    public static PhpServletScriptEngine newCloseablePhpServletScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, String protocol, int port) throws MalformedURLException {
	return new CloseablePhpServletScriptEngine(servlet, ctx, req, res, protocol, port);
    }
    public static InvocablePhpServletScriptEngine newCloseableInvocablePhpServletScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, String protocol, int port) throws MalformedURLException, URISyntaxException {
	return new CloseableInvocablePhpServletScriptEngine(servlet, ctx, req, res, protocol, port);
    }
    public static InvocablePhpServletLocalHttpServerScriptEngine newCloseableInvocablePhpServletLocalHttpServerScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, String protocol, int port) throws MalformedURLException, URISyntaxException {
	return new CloseableInvocablePhpServletLocalHttpServerScriptEngine(servlet, ctx, req, res, protocol, port);
    }   
    public static InvocablePhpServletLocalHttpServerScriptEngine newCloseableInvocablePhpServletLocalHttpServerScriptEngine(
            Servlet servlet, ServletContext ctx, HttpServletRequest req,
            HttpServletResponse res, String protocol, int port, String proxy) throws MalformedURLException, URISyntaxException {
	return new CloseableInvocablePhpServletLocalHttpServerScriptEngine(servlet, ctx, req, res, protocol, port, proxy);
    }
    public static ArrayList getManagedEngineList (HttpServletRequest req) {
	return (ArrayList) req.getAttribute(RequestListener.ROOT_ENGINES_COLLECTION_ATTRIBUTE);
    }
 }