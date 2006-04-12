/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the script continuation.
 * This class can be used to allocate php scripts on a HTTP server.
 * Although this class accidentally inherits from <code>CGIRunner</code> it doesn't necessarily run CGI binaries.
 * If you pass a URLReader, it calls its read method which opens a URLConnection to the remote server
 * and holds the allocated remote script instance hostage until release is called.
 * @author jostb
 *
 */
public class HttpProxy extends CGIRunner {
    /**
     * Create a HTTP proxy which can be used to allocate a php script from a HTTP server
     * @param reader - The reader, for example a URLReader
     * @param env - The environment, must contain values for X_JAVABRIDGE_CONTEXT. It may contain X_JAVABRIDGE_OVERRIDE_HOSTS.
     * @param ctx - The context
     */
    public HttpProxy(Reader reader, Map env, OutputStream out) {
	super("HttpProxy", reader, env, out);
    }
    
    /**
     * Create a HTTP proxy which can be used to allocate a php script from a HTTP server
     * @param reader - The reader, for example a URLReader
     * @param ctx - The context
     */
    public HttpProxy(Reader reader, String ctx, OutputStream out) {
	this(reader, (new HashMap() {
		private static final long serialVersionUID = 3257005462371971380L;
		public HashMap init(String ctx) {put("X_JAVABRIDGE_CONTEXT", ctx); return this;}}
	).init(ctx), out);
    }

    protected void doRun() throws IOException {
    	if(reader instanceof URLReader) {
	    ((URLReader)reader).read(env, out);
     	} else {
	    super.doRun();
     	}
    }
    
}
