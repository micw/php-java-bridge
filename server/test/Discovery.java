package test;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class Discovery  {
    public static void main(String[] args) throws Exception {
	System.setProperty("php.java.bridge.default_log_level", "4");
	System.setProperty("php.java.bridge.default_log_file", "");
	System.setProperty("php.java.bridge.php_exec", "php-cgi");
	StringBuffer s = new StringBuffer();
	ScriptEngineManager manager = new ScriptEngineManager();
	ScriptEngine e = manager.getEngineByName("php");
	e.put("hello", new StringBuffer("hello world"));
	e.put("s", s);
	e.eval("<?php " +
			"$s = java_context()->getAttribute('s');" +
			"$s->append(java_values(java_context()->getAttribute('hello')));" +
			"echo java_values($s);" +
			"java_context()->setAttribute('hello', '!', 100);" +
			"?>");
	s.append(e.get("hello"));
	System.out.println(e.get("hello"));
	if(!(s.toString().equals("hello world!"))) {
	  System.err.println("ERROR");
	  System.exit(1);
	}
	System.err.println("test okay");
	System.exit(0);
    }
}
