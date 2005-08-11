/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

import java.util.Map;

/**
 * This class creates a procedure proxy proxy which evaluates to a
 * dynamic proxy in coerce(). If a user has supplied a type as the
 * second argument to the closure() call, that type will be used for
 * the proxy. Otherwise the proxy is generic.
 */
public class PhpProcedureProxy {
    JavaBridge bridge;
    Map names;
    Class[] suppliedInterfaces;
    long object;

    public PhpProcedureProxy(JavaBridge bridge, Map strings, Class[] interfaces, long object) {

	this.bridge = bridge;
	this.names = strings;
	this.suppliedInterfaces = interfaces;
	this.object = object;
    }
	
    Object proxy = null;
    Object getProxy(Class[] interfaces) {
	if(proxy!=null) return proxy;
	return proxy=PhpProcedure.createProxy(bridge, names, suppliedInterfaces==null?interfaces:suppliedInterfaces, object);
    }
}

	
