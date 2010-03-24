package test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

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
	ByteArrayOutputStream errOut = new ByteArrayOutputStream();
	Writer err = new OutputStreamWriter(errOut);
	ScriptEngine eng = (new ScriptEngineManager()).getEngineByName("php-interactive");
	eng.getContext().setErrorWriter(err);
	eng.eval("$a=new java('java.util.Vector');");
	eng.eval("$a->add(1);");
	eng.eval("$a->add(2);");
	eng.eval("$a->add(3);");
	eng.eval("class C{function toString() {return 'foo';}}");
	eng.eval("$a->add(java_closure(new C()));");
	eng.eval("$b=new java('java.util.Vector');");
	eng.eval("$b->add(1);");
	eng.eval("$b->add(2);");
	eng.eval("$b->add(3);");
	try { System.out.println(eng.eval("die();")); } catch (Exception e) {System.err.println("got exception: " + e);}
	try { System.out.println(eng.eval("echo 'a:'. $a"));} catch (Exception e) {System.err.println("got exception: " + e);}
	System.out.println(eng.eval("echo 'b:'. $b"));
	eng.eval((String)null);

	eng = (new ScriptEngineManager()).getEngineByName("php-interactive");
	System.out.println(eng.eval("$a=122"));
	System.out.println(eng.eval("$a=$a+1;")); 
	System.out.println(eng.eval("echo $a;"));
	eng.eval((String)null);
	err.close();
	System.out.println("--------------\nerrors:" + errOut.toString());
    }
}
