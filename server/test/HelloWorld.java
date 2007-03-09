/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.io.Reader;
import java.io.StringReader;

import javax.script.Invocable;

import php.java.script.InvocablePhpScriptEngine;

/**
 * @author jostb
 *
 */
public class HelloWorld {

    public static void main(String[] args) {
      	String l = "3";
	System.setProperty("php.java.bridge.default_log_level", l);
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "php-cgi");

	try {
	    InvocablePhpScriptEngine engine = new InvocablePhpScriptEngine();
	    String s = "<?php extension_loaded('java')||@dl('java.so')||@dl('php_java.dll'); echo 'HelloWorld!\n'; java_context()->call(java_closure()) ||die('oops!');?>";

	    engine.eval(new StringReader(s));
	    String name = (String) ((Invocable)engine).invokeFunction("java_get_server_name", new Object[]{});
	    System.out.println("PHP/Java communication port: " + name);
	    
	    for(int i=0; i<2000; i++) {
		    engine.eval(new StringReader(s));
		    name = (String) ((Invocable)engine).invokeFunction("java_get_server_name", new Object[]{});
		    if(name==null) { System.err.println("ERROR"); System.exit(1); }

	    }
	    
	    //engine.release();
	    engine.eval((Reader)null);
	    
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
