/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletResponse;


/**
 * @author jostb
 *
 */
public class Context extends php.java.bridge.Context {
	HttpServletResponse res;
	
	public Context(HttpServletResponse res) {
		this.res = res;
	}
	
	public Writer getWriter() throws IOException {
		return res.getWriter();
	}
}
