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

    private static final String restoreState = "foreach ($javabridge_values as $javabridge_key=>$javabridge_val) {eval(\"\\$$javabridge_key=\\$javabridge_values[\\$javabridge_key];\");}\n";
    private static final String saveState = "foreach (get_defined_vars() as $javabridge_key=>$javabridge_val) {if(in_array($javabridge_key, $javabridge_ignored_keys)) continue;eval(\"\\$javabridge_values[\\$javabridge_key]=\\$$javabridge_key;\");};\n";


    /**
     * Create the interactive php script engine.
     */
    public InteractivePhpScriptEngine(InteractivePhpScriptEngineFactory factory) {
        super(factory);
    }

    boolean hasScript = false;
    /* (non-Javadoc)
     * @see javax.script.ScriptEngine#eval(java.lang.String, javax.script.ScriptContext)
     */
    /**
     * Create the interactive php script engine.
     */
    public Object eval(String script, ScriptContext context)
	throws ScriptException {
	if(script==null) {
	    hasScript = false;
	    release();
	    return null;
	}
	
	if(!hasScript) {
	    super.eval("<?php " +
		       "if(!extension_loaded('java'))@dl('java.so')||@dl('php_java.dll');\n" +
		       "ini_set('max_execution_time', 0);\n" +
		       "$javabridge_values = array();\n"+
		       "$javabridge_ignored_keys = array(\"javabridge_key\", \"javabridge_val\", \"javabridge_values\", \"javabridge_ignored_keys\", \"javabridge_param\");\n"+
		       "function javabridge_eval($javabridge_param) {\n" +
		       "global $javabridge_values;\n" +
		       "global $javabridge_ignored_keys;\n" +
		       "ob_start();\n" +
		       restoreState +
		       "eval(\"$javabridge_param\");\n" +
		       saveState +
		       "$javabridge_retval = ob_get_contents();\n" +
		       "ob_end_clean();\n" +
		       "return $javabridge_retval;\n" +
		       "};\n" +
		       "$javabridge_ctx=java_context();$javabridge_ctx->call(java_closure());unset($javabridge_ctx);\n" +
		       "?>", context);
	    hasScript = true;
	}
	script=script.trim() + ";";
	Object o = null;
	try {o=((Invocable)this).invoke("javabridge_eval", new Object[]{script});}catch(NoSuchMethodException ex){/*ignore*/};
	return o;
    }
}
