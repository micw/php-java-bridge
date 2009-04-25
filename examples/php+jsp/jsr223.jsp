<%@page import="java.io.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>

<%!
private static final Reader HELLO_SCRIPT_READER = EngineFactory.createPhpScriptReader("<?php echo 'Hello java world!'; ?>");
%>

<%
/** access the JSR 223 script engine from the current web app */
    ScriptEngine e = EngineFactory.getPhpScriptEngine (this, 
                                                    application, 
                                                    request, 
                                                    response);
/** the script engine shall use the same output as the servlet */
e.getContext().setWriter (out);

/** evaluate the script, use the file: servlet +"._cache_.php" as a script cache */
Reader reader = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_SCRIPT_READER);
e.eval (reader);
reader.close();
%>
