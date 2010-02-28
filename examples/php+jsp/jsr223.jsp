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
Reader reader = EngineFactory.createPhpScriptFileReader(getClass().getName()+"._cache_.php", HELLO_SCRIPT_READER);
try {
    e.eval (reader);
} catch (Exception ex) {
  ex.printStackTrace(new java.io.PrintWriter(out));
}
reader.close();
out.println("<br><br><code>request variables:<br>");
out.println("<br>contextPath:&nbsp;"+request.getContextPath());
out.println("<br>pathInfo:&nbsp;&nbsp;&nbsp;&nbsp;"+request.getPathInfo());
out.println("<br>servletPath:&nbsp;"+request.getServletPath());
out.println("<br>queryString:&nbsp;"+request.getQueryString());
out.println("<br>requestUri:&nbsp;&nbsp;"+request.getRequestURI());
out.println("<br>requestURL:&nbsp;&nbsp;"+request.getRequestURL());
out.println("<br>pathTranslated:&nbsp;&nbsp;"+request.getPathTranslated());
out.println("</code><br><br>");
%>
