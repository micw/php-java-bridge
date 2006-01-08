/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.io.StringReader;

import javax.script.Invocable;

import php.java.script.PhpScriptEngine;

/**
 * @author jostb
 *
 */
public class HelloWorld {

    public static void main(String[] args) {
      	String l = "4";
	System.setProperty("php.java.bridge.default_log_level", l);
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "php-cgi");

	try {
	    PhpScriptEngine engine = new PhpScriptEngine();
	    String s = "<?php extension_loaded('java')||@dl('java.so')||@dl('php_java.dll'); ini_set('java.log_level', " +l+"); echo 'HelloWorld!\n'; java_context()->call(java_closure()) ||die('oops!');?>";

	    engine.eval(new StringReader(s));
	    String name = (String) ((Invocable)engine).invoke("java_get_server_name", new Object[]{});
	    System.out.println("PHP/Java communication port: " + name);
		
	    engine.release();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
