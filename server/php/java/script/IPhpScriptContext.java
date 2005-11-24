/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;
import java.util.Map;

import php.java.bridge.Invocable;
import php.java.bridge.http.ContextFactory;

/**
 * Common methods for all PHP ScriptContexts
 * 
 * @author jostb
 *
 */
public interface IPhpScriptContext extends Invocable {
    
    /**
     * Returns the Writer
     * @return The writer
     */
    public Writer getWriter();

    /**
     * Returns the script environment.
     * @return the environment
     */
    public Map getEnvironment();

    /**
     * Returns the ContextFactory.
     * @return the context manager
     */
    public ContextFactory getContextManager();

    /**
     * Set the php continuation
     * @param kont The continuation.
     */
    public void setContinuation(HttpProxy kont);


}