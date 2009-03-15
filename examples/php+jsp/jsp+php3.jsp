<%@page import="java.io.*" %>
<%@page import="java.net.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>

<%!
/** 
 * This example demonstrates how to connect Java with a remote PHP application.
 * There must not be a firewall in between, the servlet thread pool must not be 
 * limited or twice the size of the PHP container's pool size, the PHP option 
 * "allow_url_include" and the Java <code>WEB-INF/web.xml</code> "promiscuous" 
 * option must be enabled. Both components should be behind a firewall.
 */

private static File script;
private static synchronized File getScript(String path) {
  if(script!=null) return script;
  return script=EngineFactory.getPhpScript(path, 
    new StringReader(
      "<?php function f($arg) {return phpversion(); }; ?>"));
}
%>
<%
URI remotePhpApp = new URI("http://127.0.0.1:80/JavaBridge/java/JavaProxy.php");
ScriptEngine e = 
  EngineFactory.getInvocablePhpScriptEngine (this,
                               application, request, response, remotePhpApp);
try {
 e.getContext().setWriter (out);

 // the remote PHP require()'s our PHP script through HTTP using allow_url_include, so we name it .inc instead of .php 
 FileReader reader = EngineFactory.createPhpScriptFileReader(getScript(EngineFactory.getRealPath(application, request.getServletPath())+"._cache_.inc"));
 e.eval (reader); reader.close();
 Object result=
  ((Invocable)e).invokeFunction("f", new Object[]{new Integer(2)});
  out.println ("PHP application called \"JavaBridge/java/JavaProxy.php\" responds to the injected f() function: " + result);
} catch (Exception ex) {
 out.println("Could not evaluate script on IIS/Apache port 80: "+ex);
} finally { // make sure to close() the file
 e.eval ((Reader)null); // in JDK 1.5 and above use ((Closeable)e).close() instead
}
%>
