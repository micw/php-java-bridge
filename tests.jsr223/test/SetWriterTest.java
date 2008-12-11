/*-*- mode: Java; tab-width:8 -*-*/

package test;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * @author jostb
 *
 */
public class SetWriterTest {

  public static class TestWriter extends Writer {

    public void close() throws IOException {
	throw new RuntimeException ("failed");
    }

    public void flush() throws IOException {
	
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
	System.out.println(new String (cbuf, off, len));
    }
      
  }
  public static class TestReader extends Reader {

    public void close() throws IOException {
	throw new RuntimeException ("failed");
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
	return 0;
    }
      
  }
  public static void main(String[] args) {
	try {
	    ScriptEngineManager m = new ScriptEngineManager();
	    ScriptEngine e = m.getEngineByName("php");
	    ScriptContext c = e.getContext();
	    c.setWriter(new TestWriter());
	    c.setErrorWriter(new TestWriter());
	    c.setReader(new TestReader());
	    e.eval("<?php echo 1+2; ?>");
	    
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
