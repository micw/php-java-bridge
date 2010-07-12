<%@page import="javax.script.*" %>
<%@page import="java.io.*" %>
<%@page import="php.java.script.servlet.PhpCompiledHttpScriptContext" %>
<%@page import="java.security.cert.CertStoreParameters" %>

<%!
private static final CompiledScript script;
static {
	// compile a simple PHP script to cachable opcode
	try {	 
		script = ((Compilable)(new ScriptEngineManager().getEngineByName("php-invocable"))).compile(
  				"<?php echo java_context()->get('hello'); function f($p){return (string)$p+1;};?>");
 	} catch (ScriptException e) {
   		throw new RuntimeException(e);
 	}
}
%>

<%
try {
	// clone the compiled script and connect it to the servlet ContextLoaderListener's FastCGI runner
	CompiledScript instance = (CompiledScript)((CertStoreParameters)script).clone();
	instance.getEngine().setContext(new PhpCompiledHttpScriptContext(script.getEngine().getContext(),this,application,request,response));
	// connect its output
	instance.getEngine().getContext().setWriter(out);
	instance.getEngine().getContext().setErrorWriter(out);

   	// eval the compiled script, should display "hello world!"
	instance.getEngine().put("hello", "hello world!<br>");
	instance.eval();
   	
	// eval again, should display this
	instance.getEngine().put("hello", String.valueOf(this)+"<br>");
	instance.eval();
  	
	// now invoke the defined function f, passing it integer 1. Should return 2
 	out.println(String.valueOf(((Invocable)instance.getEngine()).invokeFunction("f", new Object[]{1}))+"<br>");
  
	// It is important to release the engine here or to register it with tomcat for shutdown, otherwise tomcat will complain
  	((Closeable)instance.getEngine()).close();
} catch (Throwable t) {
  out.println(t);
}
%>
