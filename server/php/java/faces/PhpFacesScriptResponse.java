package php.java.faces;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import php.java.script.PhpScriptWriter;

public class PhpFacesScriptResponse extends HttpServletResponseWrapper {
    
    PrintWriter writer;
	
    public PhpFacesScriptResponse(PhpFacesScriptContext context, HttpServletResponse res){
        super(res);
        try {
	    this.writer = new PhpScriptWriter(res.getOutputStream());
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
    public PrintWriter getWriter() throws IOException {
    	return writer;
    }
    
}
