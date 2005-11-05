/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.servlet.ContextManager;


class PhpHttpScriptContextManager extends php.java.servlet.ContextManager {
    private final PhpHttpScriptContext	context;

    public static ContextManager addNew(PhpHttpScriptContext context, HttpServletRequest req, HttpServletResponse res) {
	PhpHttpScriptContextManager ctx = new PhpHttpScriptContextManager(context, req, res);
	ctx.add();
	return ctx;
    }
    protected PhpHttpScriptContextManager(PhpHttpScriptContext context, HttpServletRequest req, HttpServletResponse res) { 
	super(req, res);
	this.context = context; 
    }

    public Object getContext() {
	return context;
    }
}
