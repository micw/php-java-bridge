package test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class SimpleInvocation {

    public static void main(String[] args) throws Exception {
	ScriptEngineManager m = new ScriptEngineManager();
	ScriptEngine e = m.getEngineByName("php-invocable");
	Invocable i = (Invocable)e;
	i.invokeFunction("phpinfo", new Object[0]);
	System.exit(0);
    }
}
