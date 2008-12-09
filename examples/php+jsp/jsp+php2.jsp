<%@page import="java.io.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>

<%!
private static File script;
private static File getScript(String path) {
  if(script!=null) return script;
  return script=EngineFactory.getPhpScript(path, 
    new StringReader(
      "<?php function f($arg) {return 1 + java_values($arg); }; ?>"));
}
%>
<%
try {
ScriptEngine e = 
  EngineFactory.getInvocablePhpScriptEngine (this,
                               application, request, response, "http", 80);
e.getContext().setWriter (out);

FileReader reader = EngineFactory.createPhpScriptFileReader(getScript(EngineFactory.getRealPath(application, request.getServletPath())));
e.eval (reader); reader.close();
Object result=
((Invocable)e).invokeFunction("f", new Object[]{new Integer(2)});
e.eval ((Reader)null); // in JDK 1.5 and above use ((Closeable)e).close() instead
out.println("result from php::f(), printed from the servlet: " + result);
} catch (Exception ex) {
  out.println("Could not evaluate script on IIS/Apache port 80: "+ex);
}
%>
