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
    
    /**
     * Generate a new proxy for the given interface
     * @param iface The interface that the generated proxy should implement.
     * @return The PhpProcedure.
     */
    public Object getNewFromInterface(Class iface) {
        if(iface==null) throw new NullPointerException("iface");
        return PhpProcedure.createProxy(bridge, name, names, new Class[]{iface}, object);
    }
}

	
