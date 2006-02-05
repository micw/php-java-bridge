/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;

import php.java.bridge.Invocable;
import php.java.bridge.JavaBridgeRunner;
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
     * Set the context factory
     * @param factory the factory
     */
    public void setContextFactory(ContextFactory factory);


    /**
     * Returns the ContextFactory.
     * @return the context manager
     */
    public ContextFactory getContextFactory();

    /**
     * Set the php continuation
     * @param kont The continuation.
     */
    public void setContinuation(HttpProxy kont);

    /**
     * Return the http server associated with this context.
     * @return The http server.
     */
    public JavaBridgeRunner getHttpServer();

}