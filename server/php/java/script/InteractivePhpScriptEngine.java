/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2006 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

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
		       "if(!extension_loaded('java')) {(@include_once(\"java/Java.php\"))||@dl('java.so')||@dl('php_java.dll');}\n" +
		       "ini_set('max_execution_time', 0);\n" +
		       "$javabridge_values = array();\n"+
		       "$javabridge_ignored_keys = array(\"javabridge_key\", \"javabridge_val\", \"javabridge_values\", \"javabridge_ignored_keys\", \"javabridge_param\");\n"+
		       "function javabridge_eval($javabridge_param) {\n" +
		       "global $javabridge_values;\n" +
		       "global $javabridge_ignored_keys;\n" +
		       "ob_start();\n" +
		       restoreState +
		       "eval(java_cast($javabridge_param,\"s\"));\n" +
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
	try {o=((Invocable)this).invokeFunction("javabridge_eval", new Object[]{script});}catch(NoSuchMethodException ex){/*ignore*/};
	return o;
    }
}
