/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * A PrintWriter backed by an OutputStream.
 * @author jostb
 *
 */
public class PhpScriptWriter extends PrintWriter {

    OutputStream out;
	
    /**
     * Create a new PhpScriptWriter.
     * @param out The OutputStream
     */
    public PhpScriptWriter(OutputStream out) {
        super(out);
        if(out==null) throw new NullPointerException("out");
	this.out = out;
    }
	
    /**
     * Returns the OutputStream.
     * @return The OutputStream.
     */
    public OutputStream getOutputStream() {
	return out;
    }
}
