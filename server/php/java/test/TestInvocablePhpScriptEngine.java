package php.java.test;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import junit.framework.TestCase;

public class TestInvocablePhpScriptEngine extends TestCase {

    private ScriptEngine e;
    private Bindings b;
    private String script;

    public TestInvocablePhpScriptEngine(String name) {
	super(name);
    }

    protected void setUp() throws Exception {
	super.setUp();
	ScriptEngineManager m = new ScriptEngineManager();
	e = m.getEngineByName("php-invocable");
	b = new SimpleBindings();
	script = "<?php function f($arg) {return 1 + (string)$arg;}; exit(1+2); ?>";
    }

    protected void tearDown() throws Exception {
	super.tearDown();
	((Closeable)e).close();
    }

    public void testEvalReader() {
	try {
	    Reader r = new StringReader(script);
	    assertTrue("3".equals(String.valueOf(e.eval(r))));
	    r.close();
        } catch (Exception e) {
            fail(String.valueOf(e));
        }
    }

    public void testEvalReaderBindings() {
	try {
	    Reader r = new StringReader(script);
	    assertTrue("3".equals(String.valueOf(e.eval(r, b))));
	    r.close();
        } catch (Exception e) {
            fail(String.valueOf(e));
        }
    }

    public void testEvalString() {
	try {
	    assertTrue("3".equals(String.valueOf(e.eval(script))));
        } catch (ScriptException e) {
            fail(String.valueOf(e));
        }
    }
    public void testEvalStringBindings() {
	try {
	    assertTrue("3".equals(String.valueOf(e.eval(script,b))));
        } catch (ScriptException e) {
            fail(String.valueOf(e));
        }
    }
    public void testEvalCompilableString() {
	try {
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    OutputStreamWriter writer = new OutputStreamWriter(out);
	    e.getContext().setWriter(writer);
	    ((java.io.FileFilter)e).accept(new File(System.getProperty("java.io.tmpdir", "/tmp")+File.separator+"test.php"));
	    CompiledScript s = ((Compilable)e).compile("<?php echo 1+2;?>");

	    long t1 = System.currentTimeMillis();
	    for (int i=0; i<100; i++) {
		s.eval(); 
		assertTrue("3".equals(out.toString())); out.reset();
	    }
	    long t2 = System.currentTimeMillis();
	    System.out.println("testEvalCompilableString time:" + (t2-t1));

	} catch (Exception e) {
            fail(String.valueOf(e));
        }
    }
//    public void testInvokeFunction() {
//	fail("Not yet implemented");
//    }
//
//    public void testInvokeMethod() {
//	fail("Not yet implemented");
//    }
//
//    public void testGetInterfaceClass() {
//	fail("Not yet implemented");
//    }
//
//    public void testGetInterfaceObjectClass() {
//	fail("Not yet implemented");
//    }
//    public void testInvokeFunctionCompiled() {
//	fail("Not yet implemented");
//    }
//
//    public void testInvokeMethodCompiled() {
//	fail("Not yet implemented");
//    }
//
//    public void testGetInterfaceClassCompiled() {
//	fail("Not yet implemented");
//    }
//
//    public void testGetInterfaceObjectClassCompiled() {
//	fail("Not yet implemented");
//    }
}
