package test;
import java.io.Reader;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class TestInvocable {
    public static void main(String _s[]) throws Exception {
	System.setProperty("php.java.bridge.default_log_file", "");

	ScriptEngineManager manager = new ScriptEngineManager();
	ScriptEngine e = manager.getEngineByName("php-invocable");

	e.eval("<?php class f {function a($p) {return java_values($p)+1;}}\n" +
			"java_context()->setAttribute('f', java_closure(new f()), 100); ?>");

	Invocable i = (Invocable)e;
	Object f = e.getContext().getAttribute("f", 100);
	System.out.println(i.invokeMethod(f, "a", new Object[] {new Integer(1)}));

	e.eval((Reader)null);
    }
}
