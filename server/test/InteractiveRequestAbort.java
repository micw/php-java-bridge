package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import php.java.bridge.Request.AbortException;


/**
 * Check if Request.handleSubRequests uses an AbortException instead of an IOException to terminate
 * the request-handling loop.
 */

public class InteractiveRequestAbort {

    public static void main(String[] args) throws Exception {
	String devNull = new File("/dev/null").exists()? "/dev/null" : "devNull";
	System.setProperty("php.java.bridge.default_log_level", "5");
	System.setProperty("php.java.bridge.default_log_file", devNull);
	ScriptEngineManager m = new ScriptEngineManager();
	ScriptEngine e = m.getEngineByName("php-interactive");
	FileOutputStream fos = new FileOutputStream("/dev/null");
	e.getContext().setErrorWriter(new FileWriter(new File(devNull)));
	e.getContext().setWriter(new FileWriter(new File(devNull)));
	
	try {
	    e.eval("function toString() {return 'hello'; }; echo java_closure(); echo new JavaException('java.lang.Exception', 'hello'); echo JavaException('foo')");
	} catch (ScriptException ex) {
	    Throwable orig = ex.getCause();
	    if (orig instanceof AbortException) {
		System.out.println("test okay");
		System.exit(0);
	    }
	}
	System.out.println("test failed");
	System.exit(1);
	
    }

}
