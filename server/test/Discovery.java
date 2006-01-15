package test;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class Discovery  {
 	public static void main(String[] args) throws Exception {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine e = manager.getEngineByName("php");
		e.eval("<?php echo 'HelloWorld'; ?>");
	}
}
