<%@page import="javax.script.*" %>

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
  script.getEngine().getContext().setWriter(out);
  script.getEngine().getContext().setErrorWriter(out);
  script.getEngine().put("hello", "world!");
  ScriptContext ctx = new php.java.script.servlet.PhpCompiledHttpScriptContext(script.getEngine().getContext(),this,application,request,response);
  script.eval(ctx);
  script.getEngine().put("hello", String.valueOf(this));
  script.eval(ctx);
%>