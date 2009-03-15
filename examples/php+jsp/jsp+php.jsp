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
/* Create a standard script engine */
Object result = null;
ScriptEngine e = 
  EngineFactory.getInvocablePhpScriptEngine (this, 
					     application, 
					     request, 
					     response);
try {
 /* and connect its standard output */
 e.getContext().setWriter (out);

 /* evaluate the script, cache it in the file ".../jsp+php.jsp._cache_.php" */
 FileReader reader = EngineFactory.createPhpScriptFileReader(getScript(EngineFactory.getRealPath(application, request.getServletPath())+"._cache_.php"));
 e.eval (reader);
 reader.close();

/* make the script engine invocable */
 Invocable i = (Invocable) e;

/* and invoke its phpinfo and f() functions */
 result = i.invokeFunction("phpinfo", new Object[]{});
 result = i.invokeFunction("f", new Object[]{new Integer(2)});

} finally {
 /* terminate the script engine and flush its output, get its result code */
 e.eval ((Reader)null); // in JDK 1.5 and above use ((Closeable)e).close() instead
}
/* print the result from the above f() method invocation */
out.println("result from php::f(), printed from the servlet: " + result);

%>
