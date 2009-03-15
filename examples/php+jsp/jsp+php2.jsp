<%@page import="java.io.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>

<%!
private static File script;
private static synchronized File getScript(String path) {
  if(script!=null) return script;
  return script=EngineFactory.getPhpScript(path, 
    new StringReader(
      "<?php function f($arg) {return 1 + java_values($arg); }; ?>"));
}
%>
<%
ScriptEngine e = 
  EngineFactory.getInvocablePhpScriptEngine (this,
                               application, request, response, "http", 80);
try {
 e.getContext().setWriter (out);

 FileReader reader = EngineFactory.createPhpScriptFileReader(getScript(EngineFactory.getRealPath(application, request.getServletPath())+"._cache_.php"));
 e.eval (reader); reader.close();
 Object result=
  ((Invocable)e).invokeFunction("f", new Object[]{new Integer(2)});
 out.println("result from php::f(), printed from the servlet: " + result);
} catch (Exception ex) {
 out.println("Could not evaluate script on IIS/Apache port 80: "+ex);
} finally { // make sure to close() the file
 e.eval ((Reader)null); // in JDK 1.5 and above use ((Closeable)e).close() instead
}
%>
