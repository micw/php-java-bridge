package test;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class Discovery  {
    public static void main(String[] args) throws Exception {
	System.setProperty("php.java.bridge.default_log_level", "3");
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "php-cgi");


	ScriptEngineManager manager = new ScriptEngineManager();
	ScriptEngine e = manager.getEngineByName("php");
	e.put("hello", new StringBuffer("hello world"));
	e.eval("<?php echo (string)java_context()->getAttribute('hello'); java_context()->setAttribute('hello', '!', 100);?>");
	System.out.println(e.get("hello"));
    }
}
