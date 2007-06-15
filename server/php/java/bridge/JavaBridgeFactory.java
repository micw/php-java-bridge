/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import php.java.bridge.IJavaBridgeFactory;
import php.java.bridge.JavaBridge;
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
 * Create new session or context instances
 * @see php.java.bridge.Session
 * @see php.java.bridge.http.Context
 * @see php.java.servlet.Context
 * @see php.java.bridge.http.ContextFactory
 * @see php.java.servlet.ServletContextFactory
 * @see php.java.script.PhpScriptContextFactory
 * @author jostb
 *
 */
public abstract class JavaBridgeFactory implements IJavaBridgeFactory {
    
    protected JavaBridge bridge = null;

    public abstract SimpleJavaBridgeClassLoader getJavaBridgeClassLoader();
    public abstract ClassLoader getClassLoader();
    
    protected JavaBridge checkBridge() {
	return bridge;
    }
    /**
     * Return the JavaBridge.
     * @return Returns the bridge.
     */
    public JavaBridge getBridge() {
	if(bridge != null) return bridge;
	bridge=new JavaBridge(getJavaBridgeClassLoader());
	if(Util.logLevel>=4) Util.logDebug("created new bridge: " + bridge);
	bridge.setSessionFactory((SessionFactory)this);
	return bridge;
    }
}
