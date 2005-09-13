<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$session = java_session("sessionSharing");
?>

<HTML>
<TITLE>PHP and JSP session sharing</title>
<BODY>
<?php
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
