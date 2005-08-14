/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

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
	
    protected PhpProcedure(JavaBridge bridge, long object, Map names) {
	this.bridge = bridge;
	this.object = object;
	this.names = names;
    }

    public static Object createProxy(JavaBridge bridge, Map names, Class interfaces[], long object) {
	PhpProcedure handler = new PhpProcedure(bridge, object, names);
	ClassLoader loader = interfaces.length>0 ? interfaces[0].getClassLoader():bridge.cl.getClassLoader();   
	Object proxy = Proxy.newProxyInstance(loader, interfaces, handler);
	return proxy;
    }
	
    private void setResultFromProcedure(Response response, Method method, Object[] args) {
	String name = method.getName();
	String cname = (String)names.get(name);
	if(cname==null) cname=name;
	int argsLength = args==null?0:args.length;
	response.writeApplyBegin(object, cname, name, argsLength);
	for (int i=0; i<argsLength; i++) {
	    response.writePairBegin();
	    bridge.setResult(response, args[i]);
	    response.writePairEnd();
	}
	response.writeApplyEnd();
    }
	
    /* (non-Javadoc)
     * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    	if(bridge.logLevel>3) bridge.logDebug("invoking callback: " + method);
	setResultFromProcedure(bridge.request.response, method, args);
	Object[] result = null;
	result = bridge.request.handleSubRequests();
	if(bridge.logLevel>3) bridge.logDebug("result from cb: " + Arrays.asList(result));
	return bridge.coerce(new Class[] {method.getReturnType()}, result, bridge.request.response)[0];
    }
}
