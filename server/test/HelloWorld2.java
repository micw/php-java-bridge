/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.net.URL;

import javax.script.Invocable;

import php.java.script.PhpScriptEngine;
import php.java.script.URLReader;

/**
 * @author jostb
 *
 */
public class HelloWorld2 {

    public static void main(String[] args) {
	try {
	    PhpScriptEngine engine = new PhpScriptEngine();

	    engine.put("key", "testVal");
	    engine.eval(new URLReader(new URL("http://192.168.5.99:8000/HelloWorld.php")));
	    System.out.println(((Invocable)engine).call("sayHello", new Object[]{}));

	    engine.release();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
