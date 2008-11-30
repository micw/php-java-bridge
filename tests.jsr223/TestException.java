package test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class TestException {
    public static void main(String _s[]) throws Exception {
	System.setProperty("php.java.bridge.default_log_file", "");

	ScriptEngineManager manager = new ScriptEngineManager();
	ScriptEngine e = manager.getEngineByName("php");
	try {
	    e.eval("<?php bleh();?>");
	} catch (ScriptException ex) {
	    System.out.println("test okay");
	    ex.printStackTrace(System.out);
	    System.exit(0);
	}
	System.err.println("test failed");
	System.exit(1);
    }    
}
