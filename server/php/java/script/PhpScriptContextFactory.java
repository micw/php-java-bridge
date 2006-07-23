/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.ScriptContext;

import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.IContextFactory;

/**
 * A custom context factory, creates a ContextFactory for JSR223 contexts.
 * @author jostb
 *
 */
public class PhpScriptContextFactory extends php.java.bridge.http.SimpleContextFactory {

    /**
     * Add the PhpScriptContext
     * @param context
     * @return The ContextFactory.
     */
    public static IContextFactory addNew(ScriptContext context) {
	PhpScriptContextFactory ctx = new PhpScriptContextFactory();
	ctx.setContext(context);
	return ctx;
    }
    public PhpScriptContextFactory() {
	super(ContextFactory.EMPTY_CONTEXT_NAME);
    }
}
