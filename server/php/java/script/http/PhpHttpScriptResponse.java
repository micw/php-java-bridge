package php.java.script.http;

import java.io.IOException;
import java.io.PrintWriter;

import javax.script.http.HttpScriptResponse;
import javax.servlet.http.HttpServletResponse;

import php.java.script.PhpScriptWriter;

public class PhpHttpScriptResponse extends HttpScriptResponse {
    
    PrintWriter writer;
	
    public PhpHttpScriptResponse(PhpHttpScriptContext context, HttpServletResponse res){
        super(context, res);
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
