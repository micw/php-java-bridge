/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;

import php.java.bridge.Invocable;
import php.java.bridge.http.HttpServer;

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
     * Set the php continuation
     * @param kont The continuation.
     */
    public void setContinuation(HttpProxy kont);

    /**
     * Return the http server associated with this context.
     * @return The http server.
     */
    public HttpServer getHttpServer();

}