/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.servlet.ContextManager;


public class PhpFacesScriptContextManager extends php.java.servlet.ContextManager {
    private final PhpFacesScriptContext	context;

    public static ContextManager addNew(PhpFacesScriptContext context, HttpServletRequest req, HttpServletResponse res) {
	PhpFacesScriptContextManager ctx = new PhpFacesScriptContextManager(context, req, res);
	ctx.add();
	return ctx;
    }
    protected PhpFacesScriptContextManager(PhpFacesScriptContext context, HttpServletRequest req, HttpServletResponse res) { 
	super(req, res);
	this.context = context; 
    }

    public Object getContext() {
	return context;
    }
}
