<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.PhpCompiledHttpScriptContext" %>
<%@page import="php.java.script.CloneableScript" %>

<%!
private static final CompiledScript script;
static {
	try {
		script =((Compilable)(new ScriptEngineManager().getEngineByName("php-invocable"))).compile(
        "<?php function f($v){return (string)$v+1;};?>");
	} catch (ScriptException e) {
		throw new RuntimeException(e);
	}
}
%>

<%
  CompiledScript  instance = (CompiledScript)((CloneableScript)script).clone();
  instance.getEngine().setContext(new PhpCompiledHttpScriptContext(script.getEngine().getContext(),this,application,request,response));
  instance.eval();
  out.println(((Invocable)instance.getEngine()).invokeFunction("f", new Object[]{1}));
  ((java.io.Closeable)instance.getEngine()).close();
%>
