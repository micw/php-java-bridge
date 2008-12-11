package test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class GetResult {

    /**
     * @param args
     */
    public static void main(String[] args) {
	try {
	    ScriptEngine e = new ScriptEngineManager().getEngineByName("php");
	    String result = String.valueOf(e.eval("<?php exit(2+3);"));
	    if (!result.equals("5")) throw new ScriptException("test failed");

	    e = new ScriptEngineManager().getEngineByName("php-invocable");
	    Object o = e.eval("<?php exit(7+9); ?>");
	    result = String.valueOf(o); // note that this releases the engine, the next invoke will implicitly call eval() with an empty script
	    ((Invocable)e).invokeFunction("phpinfo", new Object[]{});
	    if (!result.equals("16")) throw new ScriptException("test failed");
	    System.exit(0);
	} catch (Exception e1) {
	    e1.printStackTrace();
        }
	System.exit(1);
    }

}
