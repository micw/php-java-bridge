package php.java.script;

import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;

public class InteractivePhpScriptEngineFactory extends PhpScriptEngineFactory {

  public String getLanguageName() {
    return "php-interactive";
  }
  public List getNames() {
    return Arrays.asList(new String[]{getLanguageName()});
  }

  public ScriptEngine getScriptEngine() {
      return new InteractivePhpScriptEngine(this);
  }
}
