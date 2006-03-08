/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptException;

/**
 * A convenience variant of the PHP script engine which can be used interactively.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = (new ScriptEngineManager()).getEngineByName("php-interactive);<br>
 * e.eval("$v = 1+2"); <br>
 * System.out.println(e.eval("echo $v")); <br>
 * e.eval((String)null);<br>
 * </code>
 * @author jostb
 *
 */
public class InteractivePhpScriptEngine extends PhpScriptEngine {

    private static final String restoreState = "foreach ($javabridge_values as $javabridge_key=>$javabridge_val) {eval(\"\\$$javabridge_key=\\$javabridge_values[\\$javabridge_key];\");}";
    private static final String saveState = "foreach (get_defined_vars() as $javabridge_key=>$javabridge_val) {if(in_array($javabridge_key, $javabridge_ignored_keys)) continue;eval(\"\\$javabridge_values[\\$javabridge_key]=\\$$javabridge_key;\");};";

    /**@inheritDoc*/
    public InteractivePhpScriptEngine(InteractivePhpScriptEngineFactory factory) {
        super(factory);
    }

    boolean hasScript = false;
    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.lang.String, javax.script.ScriptContext)
     */
    /**@inheritDoc*/
    public Object eval(String script, ScriptContext context)
	throws ScriptException {
	if(script==null) {
	    hasScript = false;
	    release();
	    return null;
	}
	
	if(!hasScript) {
	    super.eval("<?php " +
		       "$javabridge_values = array(); "+
		       "$javabridge_ignored_keys = array(\"javabridge_key\", \"javabridge_val\", \"javabridge_values\", \"javabridge_ignored_keys\", \"javabridge_param\"); "+
		       "function javabridge_eval($javabridge_param) { " +
		       "global $javabridge_values; " +
		       "global $javabridge_ignored_keys; " +
		       "ob_start(); " +
		       restoreState +
		       "eval(\"$javabridge_param\"); " +
		       saveState +
		       "$javabridge_retval = ob_get_contents(); " +
		       "ob_end_clean(); " +
		       "return $javabridge_retval; " +
		       "}; " +
		       "java_context()->call(java_closure()); " +
		       "?>", context);
	    hasScript = true;
	}
	script=script.trim() + ";";
	Object o = null;
	try {o=((Invocable)this).invoke("javabridge_eval", new Object[]{script});}catch(Throwable ex){ex.printStackTrace();/*ignore*/};
	return o;
    }
}
