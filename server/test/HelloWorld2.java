/*-*- mode: Java; tab-width:8 -*-*/

package test;

import php.java.script.PhpScriptEngine;

/**
 * @author jostb
 *
 */
public class HelloWorld2 {

  /** To run this test the php.ini must contain java.servlet=On and 
   * the web server document root must contain HelloWorld.php
   */
  public static void main(String[] args) {
   	String l = "6";
	System.setProperty("php.java.bridge.default_log_level", l);
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "php-cgi");

	try {
	    PhpScriptEngine engine = new PhpScriptEngine();
	    engine.eval("<?php echo new java('java.lang.String', 'hello1'); echo new java('java.lang.String', 'hello2'); ?>");
	    engine.eval("<?php echo new java('java.lang.String', 'world1'); echo new java('java.lang.String', 'world2'); ?>");
	    engine = new PhpScriptEngine();
	    engine.eval("<?php class test {}; $s = java('java.lang.String');echo Test::type();?>");
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
