/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletResponse;


/**
 * A custom context which keeps the HttpServletResponse.
 * 
 * @author jostb
 *
 */
public class Context extends php.java.bridge.http.Context {
    protected HttpServletResponse res;
	
    /**
     * Create a new context.
     * @param res The HttpServletResponse
     */
    public Context(HttpServletResponse res) {
	this.res = res;
    }
	
    public Writer getWriter() throws IOException {
	return res.getWriter();
    }
}
