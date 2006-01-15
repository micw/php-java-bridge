/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.servlet.ContextFactory;

/**
 * A custom ContextFactory, manages a custom ScriptContext.
 * @author jostb
 *
 */
public class PhpFacesScriptContextFactory extends php.java.servlet.ContextFactory {
    private final PhpFacesScriptContext	context;

    /**
     * Create a new ContextFactory
     * @param context The ScriptContext
     * @param kontext The ServletContext
     * @param req The ServletRequest
     * @param res The ServletResponse
     * @return The ContextFactory
     */
    public static ContextFactory addNew(PhpFacesScriptContext context, ServletContext kontext, HttpServletRequest req, HttpServletResponse res) {
	PhpFacesScriptContextFactory ctx = new PhpFacesScriptContextFactory(context, kontext, req, res);
	ctx.add();
	return ctx;
    }
    protected PhpFacesScriptContextFactory(PhpFacesScriptContext context, ServletContext ctx, HttpServletRequest req, HttpServletResponse res) { 
	super(ctx, req, req, res);
	this.context = context; 
    }

    /**
     * Returns the ScriptContext.
     */
    public Object getContext() {
	return context;
    }
}
