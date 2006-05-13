/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;

import php.java.bridge.Invocable;

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
     * Set the Writer
     * @param writer The writer
     */
    public void setWriter(Writer writer);

    /**
     * Set the php continuation
     * @param kont The continuation.
     */
    public void setContinuation(HttpProxy kont);
    /**
     * Get the php continuation
     * @return The HttpProxy
     */
    public HttpProxy getContinuation();
}