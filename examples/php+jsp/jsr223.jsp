<%

out.println ("hello from the servlet <br>");

javax.script.ScriptEngine e = 
  php.java.script.EngineFactory.getPhpScriptEngine (this, 
                                                    application, 
                                                    request, 
                                                    response);
e.getContext().setWriter (out);
e.eval ("<?php $x = java_context(); echo 'hello from PHP '.$x.'<br>\n'?>");

%>
