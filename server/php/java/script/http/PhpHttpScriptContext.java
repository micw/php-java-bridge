/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.http;

import java.io.IOException;
import java.io.Writer;
import java.util.Hashtable;

import javax.script.http.HttpScriptContext;
import javax.script.http.SimpleHttpScriptContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;
import php.java.servlet.ContextManager;
import php.java.servlet.PhpJavaServlet;


public class PhpHttpScriptContext extends SimpleHttpScriptContext {
    static {
	DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);
    }

	private Hashtable env;
    private ContextManager ctx;
	
    /**
     * @return Returns the context.
     */
    public ContextManager getContext() {
	return ctx;
    }


    public void release() {
	super.release();
	ctx.remove();
    }
	
    public Writer getWriter() {
	try {
	    return getResponse().getWriter();
	} catch (IOException e) {
	    return null;
	}
    }
	
    public HttpServletResponse getResponse() {
	return new PhpHttpScriptResponse(this, response);
    }


    public void initialize(Servlet servlet, HttpServletRequest req,
			   HttpServletResponse res) throws ServletException {
    	super.initialize(servlet, req, res);

    	env = new Hashtable();
	    /* send the session context now, otherwise the client has to 
	     * call handleRedirectConnection */
	    this.env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	    /* the client should connect back to us */
	    this.env.put("X_JAVABRIDGE_CONTINUATION", String.valueOf(HttpScriptContext.REQUEST_SCOPE));

    	
    	ctx = PhpHttpScriptContextManager.addNew(this, req, res);
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

}
