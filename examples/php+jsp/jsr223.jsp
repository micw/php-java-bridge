<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.PhpCompiledHttpScriptContext" %>
<%@page import="java.security.cert.CertStoreParameters" %>

<%!
private static final CompiledScript script;
static {
	try {
		script =((Compilable)(new ScriptEngineManager().getEngineByName("php"))).compile(
        "<?php echo 'Hello '.java_context()->get('hello').'!<br>'; ?>");
	} catch (ScriptException e) {
		throw new RuntimeException(e);
	}
}
%>

<%
  // create a new copy of the compiled script
  CompiledScript instance = (CompiledScript)((CertStoreParameters)script).clone();

  // create a custom ScriptContext to connect the engine to the ContextLoaderListener's FastCGI runner 
  instance.getEngine().setContext(new PhpCompiledHttpScriptContext(script.getEngine().getContext(),this,application,request,response));
  
  // connect its output
  instance.getEngine().getContext().setWriter(out);
  instance.getEngine().getContext().setErrorWriter(out);
  
  // diplay hello world
  instance.getEngine().put("hello", "world!");
  instance.eval();
  instance.getEngine().put("hello", String.valueOf(this));
  instance.eval();
%>