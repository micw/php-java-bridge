<%@page import="javax.script.*" %>
<%@page import="java.io.*" %>

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
	// eval the compiled script, should display "hello world!"
	script.getEngine().put("hello", "hello world!<br>");
       ScriptContext ctx = new php.java.script.servlet.PhpCompiledHttpScriptContext(script.getEngine().getContext(),this,application,request,response);
	script.eval(ctx);
	// eval again, should display this
	script.getEngine().put("hello", String.valueOf(this)+"<br>");
	script.eval(ctx);
  	
	// now invoke the defined function f, passing it integer 1. Should return 2
 	out.println(String.valueOf(((Invocable)script.getEngine()).invokeFunction("f", new Object[]{1}))+"<br>");
  
	// It is important to release the engine here or to register it with tomcat for shutdown, otherwise tomcat will complain
  	((Closeable)script.getEngine()).close();
} catch (Throwable t) {
  out.println(t);
}
%>
