package test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class TestExceptionInvocable2 {
    public static void main(String _s[]) throws Exception {
	System.setProperty("php.java.bridge.default_log_file", "");

	ScriptEngineManager manager = new ScriptEngineManager();
	ScriptEngine e = manager.getEngineByName("php-invocable");
	e.eval("<?php function f() { return 'f ok';}; \n" +
			"class c {function p() {return 'c::p ok';}}; \n" +
			"java_context()->setAttribute('c', java_closure(new c()), 100); " +
			"?>");

	Invocable i = (Invocable) e;
	try {
	    System.out.println(i.invokeFunction("f", new Object[] {}));
	    System.out.println("test 1 okay");
	} catch (Throwable ex) {
	    ex.printStackTrace();
	    System.exit(2);
	}
	try {
	    i.invokeFunction("g", new Object[] {});
	    System.err.println("test failed");
	    System.exit(3);
	} catch (NoSuchMethodException ex) {
	    System.out.println("test 2 okay");
//	    ex.printStackTrace(System.out);
	}
	try {
	    System.out.println(i.invokeMethod(e.get("c"), "p", new Object[] {}));
	    System.out.println("test 3 okay");
	} catch (NoSuchMethodException ex) {
	    ex.printStackTrace();
	    System.exit(4);
	}
	try {
	    i.invokeMethod(e.get("c"), "g", new Object[] {});
	    System.err.println("test failed");
	    System.exit(5);
	} catch (NoSuchMethodException ex) {
	    System.out.println("test 4 okay");
	    //ex.printStackTrace(System.out);
	}
	System.exit(0);
    }    
}
