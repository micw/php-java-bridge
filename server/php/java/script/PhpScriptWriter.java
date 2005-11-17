/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * @author jostb
 *
 */
public class PhpScriptWriter extends PrintWriter {

    OutputStream out;
	
    /**
     * @param out
     */
    public PhpScriptWriter(OutputStream out) {
	super(out, true);
	this.out = out;
    }
	
    public OutputStream getOutputStream() {
	return out;
    }
}
