/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import php.java.bridge.ContextManager;


class PhpScriptContextManager extends php.java.bridge.ContextManager {

    public static ContextManager addNew(PhpScriptContext context) {
	PhpScriptContextManager ctx = new PhpScriptContextManager();
	ctx.add();
	ctx.setContext(context);
	return ctx;
    }

    public Object getContext() {
	return context;
    }
}
