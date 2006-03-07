/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptException;

/**
 * A convenience variant of the PHP script engine which can be used interactively.<p>
 * Example:<p>
 * <code>
 * ScriptEngine e = (new ScriptEngineManager()).getEngineByName("php-interactive);<br>
 * e.eval("$v = 1+2"); <br>
 * e.eval("echo $v"); <br>
 * e.eval((String)null);<br>
 * </code>
 * @author jostb
 *
 */
public class InteractivePhpScriptEngine extends PhpScriptEngine {

    private static final int INTERACTIVE_PHP_ENGINE_SCOPE = 101;
    private static final String restoreState = "foreach (java_values(java_context()->getBindings("+INTERACTIVE_PHP_ENGINE_SCOPE+")) as $javabridge_key=>$javabridge_val) {eval(\"\\$$javabridge_key=java_context()->getAttribute(\\\"$javabridge_key\\\", "+INTERACTIVE_PHP_ENGINE_SCOPE+");\");}";
    private static final String saveState = "foreach (get_defined_vars() as $javabridge_key=>$javabridge_val) {if($javabridge_key==\"javabridge_key\" || $javabridge_key==\"javabridge_val\" || $javabridge_key==\"javabridge_param\") continue; java_context()->setAttribute($javabridge_key, $javabridge_val, "+INTERACTIVE_PHP_ENGINE_SCOPE+");};";

    /**@inheritDoc*/
    public InteractivePhpScriptEngine(InteractivePhpScriptEngineFactory factory) {
        super(factory);
    }

    protected ScriptContext getPhpScriptContext() {
      ScriptContext scriptContext = new PhpScriptContext() {
	    protected Bindings interactive_engine_scope;
	    public Bindings getBindings(int scope) {
	        if(scope==INTERACTIVE_PHP_ENGINE_SCOPE) return interactive_engine_scope;
	        else return super.getBindings(scope);
	    }
	    public void setBindings(Bindings namespace, int scope) throws IllegalArgumentException {
	        if(scope==INTERACTIVE_PHP_ENGINE_SCOPE) 
	            interactive_engine_scope = namespace;
	        else 
	            super.setBindings(namespace, scope);
	    }
	    public Object getAttribute(String name, int scope) throws IllegalArgumentException {
	        return getBindings(scope).get(name);
	    }
	    public void setAttribute(String name, Object value, int scope) throws IllegalArgumentException {
	        getBindings(scope).put(name, value);
	    }
	    public Object removeAttribute(String name, int scope) throws IllegalArgumentException {
	        return getBindings(scope).remove(name);
	    }
      };
      
      scriptContext.setBindings(createBindings(),INTERACTIVE_PHP_ENGINE_SCOPE);
      scriptContext.setBindings(createBindings(),ScriptContext.ENGINE_SCOPE);
      scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE),
				   ScriptContext.GLOBAL_SCOPE);
      
      return scriptContext;
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
		       "function javabridge_eval($javabridge_param) { " +
		       "ob_start(); " +
		       restoreState +
		       "eval(\"$javabridge_param\"); " +
		       "$ret = ob_get_contents(); " +
		       "ob_end_clean(); " +
		       saveState +
		       "return $ret; " +
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
