/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.io.FileReader;

import javax.script.Invocable;

import php.java.script.PhpScriptEngine;

/**
 * @author jostb
 *
 */
public class HelloWorld {

    public static void main(String[] args) {
	System.setProperty("php.java.bridge.default_log_level", "2");
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "/home/src/PHP-JAVA-BRIDGE/PHP5/dist/bin/php.sh");

	try {
	    PhpScriptEngine engine = new PhpScriptEngine();
	    engine.put("key", "testVal");
	    engine.eval(new FileReader("test/HelloWorld.php"));

	    Invocable eng = (Invocable)engine;
	    Object o = (eng.call("getBar", new Object[] {}));
	    eng.call("hello", o, new Object[]{"one", "two"});
	    System.out.println(eng.call("java_get_server_name", new Object[]{}));
		
	    engine.release();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
