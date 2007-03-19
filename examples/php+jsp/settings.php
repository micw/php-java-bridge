<html>
<?php 
if(!extension_loaded('java')) require_once("java/Java.inc");
$CGIServlet = new JavaClass("php.java.servlet.CGIServlet");
$Util = new JavaClass("php.java.bridge.Util");
$ctx = java_context();
/* get the current instance of the JavaBridge, ServletConfig and Context */
$bridge = $ctx->getAttribute(  "php.java.bridge.JavaBridge",      100);
$config = $ctx->getAttribute ( "php.java.servlet.ServletConfig",  100);
$context = $ctx->getAttribute( "php.java.servlet.ServletContext", 100);
?>
<head>
   <title>PHP/Java Bridge settings</title>
</head>
<body bgcolor="#FFFFFF">
<H1>PHP/Java Bridge settings</H1>
<p>
The PHP/Java Bridge web application contains two servlets. The <code>PhpJavaServlet</code> handles requests from remote PHP scripts running in Apache/IIS or from the command line. 
The second servlet <code>PhpCGIServlet</code> can handle requests from internet clients directly. 
<p>
The following shows the settings of the <code>PhpJavaServlet</code> and the <code>PhpCGIServlet</code>.
</p>
<H2>PhpJavaServlet</H2>
<p>
The <code>PhpJavaServlet</code> handles requests from PHP clients.
<blockquote>
<code>
Apache/IIS/console::PHP &lt;--&gt; PhpJavaServlet
</code>
</blockquote>

It listens for PHP/Java Bridge protocol requests on the local interface or on all available network interfaces and invokes Java methods or procedures. The following example accesses the bridge listening on the <strong>local</strong> interface:
<blockquote>
<code>
&lt;?php <br>
require_once("http://localhost:8080/JavaBridge/java/Java.inc");<br>
$System = new JavaClass("java.lang.System");<br>
echo $System->getProperties();<br>
?&gt;
</code>
</blockquote>

</p>
<table BORDER=1 CELLSPACING=5 WIDTH="85%" >
<tr VALIGN=TOP>
<th>Option</th>
<th>Value</th>
<th WIDTH="60%">Description</th>
</tr>
<tr>
<td>servlet_log_level</td>
<td><?php echo $bridge->getlogLevel();?></td>
<td>The request log level.</td>
</tr>
<tr>
<td>promiscuous</td>
<td><?php echo $Util->JAVABRIDGE_PROMISCUOUS ? "On" : "Off" ?></td>
<td>Shall the bridge accept requests from <strong>non-local</strong> PHP scripts?</td>
</tr>
</table>
</p>
<p>
<H2>PhpCGIServlet</H2>
<p>
The <code>PhpCGIServlet</code> runs PHP scripts within the J2EE/Servlet engine.
</p>
<blockquote>
<code>
internet browser &lt;--&gt; PhpCGIServlet &lt;--&gt; php-cgi &lt;--&gt; PhpJavaServlet
</code>
</blockquote>
<p>
It starts a PHP FastCGI server, if possible and necessary. Requests for PHP scripts are delegated to the FastCGI server. If the PHP code contains Java calls, the PHP/Java Bridge protocol requests are delegated back to the current VM, to an instance of the <code>PhpJavaServlet</code>.
</p>
<table BORDER=1 CELLSPACING=5 WIDTH="85%" >
<tr VALIGN=TOP>
<th>Option</th>
<th>Value</th>
<th WIDTH="60%">Description</th>
</tr>
<tr>
<td>override_hosts</td>
<td><?php $val=$config->getInitParameter("override_hosts"); echo $val?$val:"On"?></td>
<td>Should the servlet engine delegate protocol requests back to the current VM?</td>
</tr>

<tr>
<td>php_exec</td>
<td><?php $val=java_values($config->getInitParameter("php_exec")); echo $val?$val:"php-cgi"?></td>
<td>The PHP FastCGI or CGI binary.</td>
</tr>

<tr>
<td>max_requests</td>
<td><?php $val=java_values($config->getInitParameter("max_requests")); echo $val?$val:"50"?></td>
<td>How many parallel requests should the servlet engine handle?</td>
</tr>


<tr>
<td>use_fast_cgi</td>
<td><?php $val=java_values($config->getInitParameter("use_fast_cgi")); echo $val?$val:"Autostart"?></td>
<td>Shall the bridge start an internal, or use an external or no PHP FastCGI server?</td>
</tr>

<tr>
<td>shared_fast_cgi_pool</td>
<td><?php $val=java_values($config->getInitParameter("shared_fast_cgi_pool")); echo $val?$val:"Off"?></td>
<td>Are there two bridges accessing the same FastCGI pool? Set this to On in <strong>both</strong>, the global web.xml and in the WEB-INF/web.xml of the JavaBridge context.</td>
</tr>

</table>
</p>

The settings were taken from the <a href="file://<?php 
echo java_values($CGIServlet->getRealPath($context, '/WEB-INF/web.xml'))
?>">WEB-INF/web.xml</a>.
</body>
</html>
