/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Hashtable;

import php.java.bridge.SessionFactory;

/**
 * This class can be used to allocate php scripts on a HTTP server.
 * 
 * @author jostb
 *
 */
public class HttpProxy extends CGIRunner {
    /**
     * Create a HTTP proxy which can be used to allocate a php script from a HTTP server
     * @param reader - The reader, for example a URLReader
     * @param env - The environment, must contain values for X_JAVABRIDGE_CONTEXT. It may contain X_JAVABRIDGE_OVERRIDE_HOSTS.
     * @param ctx - The context
     * @param OutputStream - The OutputStream
     */
    public HttpProxy(Reader reader, Hashtable env, SessionFactory ctx, OutputStream out) {
	super("HttpProxy", reader, env, ctx, out);
    }

    protected void doRun() throws IOException {
    	if(reader instanceof URLReader) {
	    ((URLReader)reader).read( env, out);
     	} else {
	    super.doRun();
     	}
    }
    
}
