package php.java.bridge.test;

import java.io.File;
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

public class PhpScriptEngine extends TestCase {

    private ScriptEngine e;
    private Bindings b;
    private String script;

    public PhpScriptEngine(String name) {
	super(name);
    }

    protected void setUp() throws Exception {
	super.setUp();
	ScriptEngineManager m = new ScriptEngineManager();
	e = m.getEngineByName("php");
	b = new SimpleBindings();
	script = "<?php exit(1+2);?>";
    }

    protected void tearDown() throws Exception {
	super.tearDown();
	e.eval((Reader)null);
    }

    public void testEvalReader() {
	try {
	    assertTrue("3".equals(String.valueOf(e.eval(new StringReader(script)))));
        } catch (ScriptException e) {
            fail(String.valueOf(e));
        }
    }

    public void testEvalReaderBindings() {
	try {
	    assertTrue("3".equals(String.valueOf(e.eval(new StringReader(script),b))));
        } catch (ScriptException e) {
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
	    ((java.io.FileFilter)e).accept(new File(System.getProperty("java.io.tmpdir", "/tmp")+File.separator+"test.php"));
	    CompiledScript s = ((Compilable)e).compile(script);
	    assertTrue("3".equals(String.valueOf(s.eval())));
        } catch (Exception e) {
            fail(String.valueOf(e));
        }
    }

}
