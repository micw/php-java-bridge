<%!
/* The following code makes sure that the PHP script is generated with the servlet instance.
   When the servlet is generated, a file .../jsr223.jsp._cache_.php appears */

private static java.io.File helloScript = null;

/** return a new instance of the php hello script, or the cached script */
private static java.io.File getHelloScript(String path) {
 if (helloScript!=null) return helloScript;
 return helloScript=php.java.script.servlet.EngineFactory.getPhpScript(path, 
        new java.io.StringReader("<?php echo 'Hello java world!'; ?>"));
}
%>

<%
/** access the JSR 223 script engine from the current web app */
javax.script.ScriptEngine e = 
  php.java.script.servlet.EngineFactory.getPhpScriptEngine (this, 
                                                    application, 
                                                    request, 
                                                    response);
/** the script engine shall use the same output as the servlet */
e.getContext().setWriter (out);

/** evaluate the script, use the file: servlet +"._cache_.php" as a script cache */
java.io.File script = getHelloScript(application.getRealPath(request.getServletPath()));
java.io.FileReader reader = php.java.script.servlet.EngineFactory.createPhpScriptFileReader(script);
e.eval (reader);
reader.close();
%>
