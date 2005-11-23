/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.ScriptContext;

import php.java.bridge.http.ContextFactory;

/**
 * A custom context factory, creates a ContextFactory for JSR223 contexts.
 * @author jostb
 *
 */
public class PhpScriptContextFactory extends php.java.bridge.http.ContextFactory {

    /**
     * Add the PhpScriptContext
     * @param context
     * @return The ContextFactory.
     */
    public static ContextFactory addNew(ScriptContext context) {
	PhpScriptContextFactory ctx = new PhpScriptContextFactory();
	ctx.add();
	ctx.setContext(context);
	return ctx;
    }
}
