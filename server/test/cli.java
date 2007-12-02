package test;

import java.io.IOException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class cli {
    /**
     * @param args
     * @throws IOException 
     * @throws ScriptException 
     */
    public static void main(String[] args) throws IOException, ScriptException {
	ScriptEngine eng = (new ScriptEngineManager()).getEngineByName("php-interactive");
	System.out.println(eng.eval("$a=122"));
	try {
		System.out.println(eng.eval("die();")); 
	} catch (Exception e) {}
	System.out.println(eng.eval("echo $a;"));
	eng.eval((String)null);

	eng = (new ScriptEngineManager()).getEngineByName("php-interactive");
	System.out.println(eng.eval("$a=122"));
	System.out.println(eng.eval("$a=$a+1;")); 
	System.out.println(eng.eval("echo $a;"));
	eng.eval((String)null);
    }
}
