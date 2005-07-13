/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;

/**
 * This class takes the supplied PHP environment and creates a dynamic
 * proxy for calling PHP code.
 */
public class PhpProcedure implements InvocationHandler {
	
    private JavaBridge bridge;
    private HashMap map;
    private long object;
	
    protected PhpProcedure(JavaBridge bridge, long object) {
	this.bridge = bridge;
	this.object = object;
    }

    private void addMethods(Class interfaces[]) {
	this.map = new HashMap();
	for(int k=0; k<interfaces.length; k++) {
	    Method methods[] = interfaces[k].getMethods();
	    for(int i=0; i<methods.length; i++) {
		map.put(methods[i], new Integer(i));
	    }
	}
    }
    public static Object createProxy(JavaBridge bridge, String names[], Class interfaces[], long object) {
	PhpProcedure handler = new PhpProcedure(bridge, object);
	Object proxy = Proxy.newProxyInstance(bridge.cl.getClassLoader(), interfaces, handler);
	handler.addMethods(interfaces);
	return proxy;
    }
	
	private void setResultFromProcedure(Response response, Method method, Object[] args) {
		Integer pos = (Integer)(map.get(method));
		int nr = pos==null?pos.intValue():-1;
		int argsLength = args==null?0:args.length;
		response.writeApplyBegin(object, nr, method.getName(), argsLength);
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
	setResultFromProcedure(bridge.request.response, method, args);
	return bridge.coerce(new Class[] {method.getReturnType()}, bridge.request.handleSubRequests(), bridge.request.response)[0];
    }
	    
}
