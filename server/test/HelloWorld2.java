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

  /** To run this test the php.ini must contain java.servlet=On and 
   * the web server document root must contain HelloWorld.php
   */
  public static void main(String[] args) {
	try {
	    PhpScriptEngine engine = new PhpScriptEngine();

	    engine.put("key", "testVal");
	    engine.eval(new URLReader(new URL("http://192.168.5.203:80/HelloWorld.php")));
	    System.out.println(((Invocable)engine).invokeFunction("sayHello", new Object[]{}));

	    engine.release();
	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("Please make sure that php.ini contains java.servlet=On and that HelloWorld.php exists in the web server document root.");
	    System.err.println("On Security Enhanced Linux also check the audit log, switch off SEL protection with \"setenforce 0\", restart apache with \"service httpd restart\" and run the test again. Switch back with \"setenforce 1\" and extract the required permissions with audit2allow");
	}
    }
}
