/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Hashtable;

import php.java.bridge.SessionFactory;


public class HttpProxy extends CGIRunner {
    /**
	 * @param reader
	 * @param context
	 * @param out
	 */
	public HttpProxy(Reader reader, Hashtable env, SessionFactory ctx, OutputStream out) {
		super(reader, env, ctx, out);
	}

public void doRun() throws IOException {
    	if(reader instanceof URLReader) {
    	   	    	((URLReader)reader).read(this, env, ctx, out);
     	} else {
     		super.doRun();
     	}
    }
    
 }
