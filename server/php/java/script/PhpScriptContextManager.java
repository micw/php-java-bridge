/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.ScriptContext;

import php.java.bridge.ContextManager;


class PhpScriptContextManager extends php.java.bridge.ContextManager {

    /**
     * Add the PhpScriptContext
     * @param context
     * @return
     */
    public static ContextManager addNew(ScriptContext context) {
	PhpScriptContextManager ctx = new PhpScriptContextManager();
	ctx.add();
	ctx.setContext(context);
	return ctx;
    }
}
