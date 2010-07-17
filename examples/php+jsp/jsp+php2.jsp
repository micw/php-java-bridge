
<%@page import="javax.script.*" %>
<%@page import="java.net.*" %>
<%@page import="php.java.script.servlet.PhpCompiledHttpScriptContext" %>
<%@page import="php.java.script.URLReader"%>

<%!
private static final ScriptEngineManager scriptManager = new ScriptEngineManager();
%>

<%
  // create a new copy of the compiled script
  ScriptEngine instance = scriptManager.getEngineByName("php-invocable");
  try {
	  // create a custom ScriptContext to connect the engine to the ContextLoaderListener's FastCGI runner 
	  instance.setContext(new PhpCompiledHttpScriptContext(instance.getContext(),this,application,request,response));
	  URI remotePhpApp = new URI(request.getScheme(), null, "127.0.0.1", request.getLocalPort(), "/JavaBridge/java/JavaProxy.php", null, null);
	  instance.eval(new URLReader(remotePhpApp.toURL()));
	  Object result = ((Invocable)instance).invokeFunction("phpversion", new Object[]{});
	  out.println ("PHP application called \"JavaBridge/java/JavaProxy.php\" responds to phpversion(): " + result);
	  // release the resources immediately
  } catch (Exception ex) {
	  out.println("Could not evaluate script: "+ex);
  } finally {
  	((java.io.Closeable)instance).close();
  }
%>
