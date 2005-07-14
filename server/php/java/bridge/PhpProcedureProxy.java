/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

/**
 * This class creates a procedure proxy proxy which evaluates to a
 * dynamic proxy in coerce(). If a user has supplied a type as the
 * second argument to the closure() call, that type will be used for
 * the proxy. Otherwise the proxy is generic.
 */
public class PhpProcedureProxy {
	JavaBridge bridge;
	String names[];
	Class[] suppliedInterfaces;
	long object;

	public PhpProcedureProxy(JavaBridge bridge, String[] strings, Class[] interfaces, long object) {
		this.bridge = bridge;
		this.names = strings;
		this.suppliedInterfaces = interfaces;
		this.object = object;
	}
	
	Object getProxy(Class[] interfaces) {
		return PhpProcedure.createProxy(bridge, names, suppliedInterfaces==null?interfaces:suppliedInterfaces, object);
	}
}

	
