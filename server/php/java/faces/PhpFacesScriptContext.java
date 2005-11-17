/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import java.util.Hashtable;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.Invocable;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;
import php.java.script.HttpProxy;
import php.java.script.PhpSimpleHttpScriptContext;
import php.java.servlet.ContextManager;
import php.java.servlet.PhpJavaServlet;


public class PhpFacesScriptContext extends PhpSimpleHttpScriptContext implements Invocable {
    static {
	DynamicJavaBridgeClassLoader.initClassLoader(Util.DEFAULT_EXTENSION_DIR);
    }
    private Hashtable env;
    private ContextManager ctx;
    private PhpFacesScriptResponse scriptResponse;
    private HttpProxy kont;
	
 
    public void release() {
	ctx.remove();
    }
	
    public void initialize(ServletContext kontext,
			   HttpServletRequest req,
			   HttpServletResponse res) throws ServletException {

    	super.initialize(kontext, req, res);

    	scriptResponse = new PhpFacesScriptResponse(this, response);
    	
    	ctx = PhpFacesScriptContextManager.addNew(this, req, res);

    	env = new Hashtable();
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	this.env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	
    	JavaBridge bridge = new JavaBridge();
	ctx.setBridge(bridge);
    	bridge.setClassLoader(new JavaBridgeClassLoader(ctx.getBridge(), DynamicJavaBridgeClassLoader.newInstance(PhpJavaServlet.class.getClassLoader())));
    	bridge.setSessionFactory(ctx);
    }
    /**
     * @return
     */
    public Hashtable getEnvironment() {
	return env;
    }


    /**
     * @return
     */
    public SessionFactory getContextManager() {
	return ctx;
    }

    public HttpServletResponse getResponse() {
	return scriptResponse;
    }
		
    /**
     * Set the php continuation
     * @param kont - The continuation.
     */
    public void setContinuation(HttpProxy kont) {
	this.kont = kont;
    }


    /* (non-Javadoc)
     * @see php.java.bridge.Invocable#call(php.java.bridge.PhpProcedureProxy)
     */
    public boolean call(PhpProcedureProxy kont) throws InterruptedException {
	this.kont.call(kont);
	return true;
    }


}
