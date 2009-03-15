<%@page import="java.io.*" %>
<%@page import="java.net.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>
<%
/** 
 * This example demonstrates how to connect Java with a remote PHP application.
 * There must not be a firewall in between, the servlet thread pool must not be 
 * limited or twice the size of the PHP container's pool size, the PHP option 
 * "allow_url_include" and the Java <code>WEB-INF/web.xml</code> "promiscuous" 
 * option must be enabled. Both components should be behind a firewall.
 */

URI remotePhpApp = new URI("http://127.0.0.1:80/JavaBridge/java/JavaProxy.php");
ScriptEngine e = 
  EngineFactory.getInvocablePhpScriptEngine (this,
                               application, request, response, remotePhpApp);
try {
  Object result = ((Invocable)e).invokeFunction("phpversion", new Object[]{});
  out.println ("PHP application called \"JavaBridge/java/JavaProxy.php\" responds to phpversion(): " + result);

} catch (Exception ex) {
 out.println("Could not evaluate script on port 80: "+ex);
} finally { // make sure to close() the file
 e.eval ((Reader)null); // in JDK 1.5 and above use ((Closeable)e).close() instead
}
%>
