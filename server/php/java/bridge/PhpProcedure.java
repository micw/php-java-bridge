/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

/**
 * This class takes the supplied PHP environment and creates a dynamic
 * proxy for calling PHP code.
 */
public class PhpProcedure implements InvocationHandler {
	
    private JavaBridge bridge;
    private long object;
    private Map names;
    protected String name;
	
    protected PhpProcedure(JavaBridge bridge, long object, String name, Map names) {
	this.bridge = bridge;
	this.object = object;
	this.names = names;
	this.name = name;
    }

    /**
     * Called from java_closure().
     * @param bridge - The request handling bridge
     * @param name - The name, e.g. java_closure($this, "alwaysCallMe")
     * @param names - A map of names, e.g. java_closure($this, array("javaName1" => "php_name1", ...);
     * @param interfaces - The list of interfaces that this proxy must implement, may be empty. E.g. java_closure($this, null, null, array(new Java("java.awt.event.ActionListener"));
     * @param object - An opaque object ID (protocol-level).
     * @return A new proxy instance.
     */
    protected static Object createProxy(JavaBridge bridge, String name, Map names, Class interfaces[], long object) {
	PhpProcedure handler = new PhpProcedure(bridge, object, name, names);
	ClassLoader loader = interfaces.length>0 ? interfaces[0].getClassLoader():Util.getContextClassLoader();
	Object proxy = Proxy.newProxyInstance(loader, interfaces, handler);
	return proxy;
    }
	
    private Object invoke(Object proxy, String method, Class returnType, Object[] args) throws Throwable {
    	if(bridge.logLevel>3) bridge.logDebug("invoking callback: " + method);
	String cname;
	if(name!=null) {
	    cname=name;
	} else {
	    cname = (String)names.get(method);
	    if(cname==null) cname=method;
	}
	bridge.request.response.setResultProcedure(object, cname, method, args);
	Object[] result = bridge.request.handleSubRequests();
	if(bridge.logLevel>3) bridge.logDebug("result from cb: " + Arrays.asList(result));
	return bridge.coerce(returnType, result[0], bridge.request.response);
    }

    /**
     * Invoke a PHP function or a PHP method.
     * @param proxy The php environment or the PHP object
     * @param method The php method name
     * @param args the arguments
     * @return the result or null.
     * @throws a script exception.
     */
    public Object invoke(Object proxy, String method, Object[] args) throws Throwable {
	Thread thread = Thread.currentThread();
	ClassLoader loader = thread.getContextClassLoader();
	try {
	    try { thread.setContextClassLoader(bridge.getClassLoader().getClassLoader()); } catch (SecurityException e) {/*ignore*/}
	    return invoke(proxy, method, Object.class, args);
	} finally {
	  try { thread.setContextClassLoader(loader); } catch (SecurityException e) {/*ignore*/}
	}
    }

    /* (non-Javadoc)
     * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
	return invoke(proxy, method.getName(), method.getReturnType(), args);
    }
}
