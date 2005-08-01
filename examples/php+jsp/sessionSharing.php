<HTML>
<TITLE>PHP and JSP session sharing</title>
<BODY>
<?php

$session = java_session("sessionSharing");
if($session->get("counter")==null) {
  $session->put("counter", new java("java.lang.Integer", 1));
}

$counter = $session->get("counter");
print "HttpSession variable \"counter\": $counter<br>\n";
$next = new java("java.lang.Integer", $counter+1);
$session->put("counter", $next);
?>
<a href="sessionSharing.jsp">JSP page</a>
</BODY>
</HTML>
