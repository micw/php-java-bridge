package php.java.script;

import java.io.IOException;

import javax.script.Compilable;
import javax.script.ScriptEngine;

public interface IPhpScriptEngine extends ScriptEngine, Compilable, java.io.FileFilter {
    public void close() throws IOException;
    public void release();
}
