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

import java.util.Map;

/**
 * This class creates a procedure proxy proxy which evaluates to a
 * dynamic proxy in coerce(). If a user has supplied a type as the
 * second argument to the closure() call, that type will be used for
 * the proxy. Otherwise the proxy is generic.
 */
public final class PhpProcedureProxy {
    JavaBridge bridge;
    Map names = null;
    String name = null;
    Class[] suppliedInterfaces;
    long object;

    protected PhpProcedureProxy(JavaBridge bridge, Map strings, Class[] interfaces, long object) {

	this.bridge = bridge;
	this.names = strings;
	this.suppliedInterfaces = interfaces;
	this.object = object;
    }
    protected PhpProcedureProxy(JavaBridge bridge, String string, Class[] interfaces, long object) {

	this.bridge = bridge;
	this.name = string;
	this.suppliedInterfaces = interfaces;
	this.object = object;
    }
	
    Object proxy = null;
    /**
     * Generate a proxy.
     * @param interfaces The list of interfaces that the generated proxy should implement.
     * @return The PhpProcedure.
     */
    public Object getProxy(Class[] interfaces) {
	if(proxy!=null) return proxy;
	return proxy=PhpProcedure.createProxy(bridge, name, names, suppliedInterfaces==null?interfaces:suppliedInterfaces, object);
    }
    
    private static final Class[] EMPTY_INTERFACE = new Class[0];
    /**
     * Generate a new proxy for the given interface
     * @param iface The interface that the generated proxy should implement.
     * @return The PhpProcedure.
     */
    public Object getNewFromInterface(Class iface) {
        Class[] ifaces = iface==null ? EMPTY_INTERFACE : new Class[]{iface};
        return PhpProcedure.createProxy(bridge, name, names, ifaces, object);
    }
}

	
