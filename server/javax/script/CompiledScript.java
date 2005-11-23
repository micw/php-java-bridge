
package javax.script;

/**
 * ComplieScript interface is an abstraction for the intermediate 
 * code produced by the compilation and contains methods which allow
 * the re-execution of the intermediate code retained.
 * 
 * @author Nandika Jayawardana <nandika@opensource.lk>
 * @author Sanka Samaranayake  <sanka@opensource.lk>
 */
public abstract class CompiledScript {
    
    public CompiledScript(){
    }
    
    /**
     * Re-evaluates the pre-compiled script stored using the 
     * ENGINE_SCOPE and the GLOBAL_SCOPE of the associated 
     * ScriptEngine and returns the resultant object.
     * 
     * @return the resultant object after the re-evaluation of the 
     *         script
     * @throws ScriptException if re-evaluation fails due to any 
     *         reason
     */ 
    public Object eval() throws ScriptException {
        
        SimpleScriptContext context = new SimpleScriptContext();
        
        context.setNamespace(getEngine().getNamespace(ScriptContext.ENGINE_SCOPE),
                        ScriptContext.ENGINE_SCOPE);
        context.setNamespace(getEngine().getNamespace(ScriptContext.GLOBAL_SCOPE),
                        ScriptContext.GLOBAL_SCOPE);
        
        return eval(context);
    }    
    
    /**
     * Re-evaluates the pre-compiled script using the specified 
     * namespace as the SCRIPT_SCOPE and using ENGINE_SCOPE, 
     * GLOBAL_SCOPE of the associated ScriptEngine.
     *   
     * @param namespace the namespace to be used as the SCRIPT_SCOPE
     * @return resultant object after the re-evaluation
     * @throws ScriptException if the re-evaluation fails due to any
     *         reason
     */
    public Object eval(Bindings namespace) throws ScriptException{
        
        SimpleScriptContext context = new SimpleScriptContext();
        
        if(namespace==null) namespace = getEngine().getNamespace(ScriptContext.ENGINE_SCOPE); 
        context.setNamespace(namespace, ScriptContext.ENGINE_SCOPE);
        context.setNamespace(getEngine().getNamespace(ScriptContext.GLOBAL_SCOPE),
                        ScriptContext.GLOBAL_SCOPE);
        
        return eval(context);
    }
    
    /**
     * Re-evaluates the recompiled script using the specified 
     * ScriptContext. 
     * 
     * @param context A ScriptContext to be used in the re-evalution
     *        of the script
     * @return resultant object after the re-evaluation
     * @throws ScriptException if the re-evaluation fails due to any
     *         reason
     */
    public abstract Object eval(ScriptContext context) throws ScriptException;
        
    /**
     * Retrieves a reference to the ScriptEngine whose methods 
     * created this CompiledScript object.
     * 
     * @return the ScriptEngine which created this CompiledScript
     *         object
     */
    public abstract ScriptEngine getEngine();
    
}
