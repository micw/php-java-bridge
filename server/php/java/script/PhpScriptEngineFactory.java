package php.java.script;

import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import php.java.bridge.Util;

public class PhpScriptEngineFactory implements ScriptEngineFactory {

  public String getEngineName() {
    return "PHP";
  }

  public String getEngineVersion() {
    return Util.VERSION;
  }

  public String getLanguageName() {
    return "php";
  }

  public String getLanguageVersion() {
    return "5";
  }

  public List getExtensions() {
    return getNames();
  }

  public List getMimeTypes() {
    return Arrays.asList(new String[]{});
  }

  public List getNames() {
    return Arrays.asList(new String[]{"php"});
  }

  public ScriptEngine getScriptEngine() {
      return new PhpScriptEngine(this);
  }

  public Object getParameter(String key) {
        if(key.equals("javax.script.name"))
            return getLanguageName();
        if(key.equals("javax.script.engine"))
            return getEngineName();
        if(key.equals("javax.script.engine_version"))
            return getEngineVersion();
        if(key.equals("javax.script.language"))
            return getLanguageName();
        if(key.equals("javax.script.language_version"))
            return getLanguageVersion();
        if(key.equals("THREADING"))
            return "STATELESS";
        else
            throw new IllegalArgumentException("key");
  }

  public String getMethodCallSyntax(String obj, String m, String[] args) {
      StringBuffer b = new StringBuffer();
      b.append("$");
      b.append(obj);
      b.append("->");
      b.append(m);
      b.append("(");
      int i;
      for(i=0; i<args.length-1; i++) {
	  b.append(args[i]);
	  b.append(",");
      }
      b.append(args[i]);
      b.append(")");
      return b.toString();
  }

  public String getOutputStatement(String toDisplay) {
      return "echo("+toDisplay+")";
  }

  public String getProgram(String[] statements) {
      int i=0;
      StringBuffer b = new StringBuffer("<?php ");
      
      for(i=0; i<statements.length; i++) {
	  b.append(statements[i]);
	  b.append(";");
      }
      b.append("?>");
      return b.toString();
  }

}
