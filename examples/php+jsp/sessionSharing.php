<?php require_once("java/Java.inc");
java_autoload();

$session = java_session();
?>

<HTML>
<TITLE>PHP and JSP session sharing</title>
<BODY>
<?php
if(is_null($session->get("counter"))) {
  $session->put("counter", new java_lang_Integer(1));
}

$counter = java_values($session->get("counter"));
print "HttpSession variable \"counter\": $counter<br>\n";
$next = new java_lang_Integer($counter+1);
$session->put("counter", $next);
?>
<a href="sessionSharing.jsp">JSP page</a>
</BODY>
</HTML>
