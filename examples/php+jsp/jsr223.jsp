<%@page import="java.io.*" %>
<%@page import="javax.script.*" %>
<%@page import="php.java.script.servlet.EngineFactory" %>

<%!
/* The following code makes sure that the PHP script is generated with the servlet instance.
   When the servlet is generated, a file .../jsr223.jsp._cache_.php appears */

private static File helloScript = null;

/** return a new instance of the php hello script, or the cached script */
private static File getHelloScript(String path) {
 if (helloScript!=null) return helloScript;
 return helloScript = EngineFactory.getPhpScript(path, 
        new StringReader("<?php echo 'Hello java world!'; ?>"));
}
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
FileReader reader = EngineFactory.createPhpScriptFileReader(getHelloScript(EngineFactory.getRealPath(application, request.getServletPath())));
e.eval (reader);
reader.close();
%>
