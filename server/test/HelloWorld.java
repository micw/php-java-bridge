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

    public interface i {
	void hello(Object a, Object b);
    }

    public static void main(String[] args) {
	System.setProperty("php.java.bridge.default_log_level", "2");
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "/opt/jakarta-tomcat-4.0.2-b2/webapps/JavaBridge/WEB-INF/cgi/php");

	try {
	    PhpScriptEngine engine = new PhpScriptEngine();
	    engine.put("key", "testVal");
	    engine.eval(new FileReader("test/HelloWorld.php"));

	    Invocable eng = (Invocable)engine;
	    Object o = (eng.call("getBar", new Object[] {}));
	    eng.call("hello", o, new Object[]{"one", "two"});
		
	    engine.release();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
