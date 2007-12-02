package test;

import java.io.IOException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class Script {
  /**
   * @param args
   * @throws IOException 
   * @throws ScriptException 
   */
  public static void main(String[] args) throws IOException, ScriptException {
      ScriptEngine eng = (new ScriptEngineManager()).getEngineByName("php");
      System.out.println(eng.eval("<?php java_context()->call(java_closure()) || print('test okay'); ?>"));
      eng.eval((String)null);
  }
}
