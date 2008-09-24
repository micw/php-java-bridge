<%

out.println ("hello from the servlet <br>");

javax.script.ScriptEngine e = 
  php.java.script.servlet.EngineFactory.getPhpScriptEngine (this, 
                                                    application, 
                                                    request, 
                                                    response);
e.getContext().setWriter (out);
e.eval (
    "<?php "+
     "require_once ($_SERVER['DOCUMENT_ROOT'].'/java/Java.inc');" +
     "$ctx = java_context();" +
     "echo 'hello from PHP '.$ctx.'<br>\n'"+
    "?>"
);

%>
