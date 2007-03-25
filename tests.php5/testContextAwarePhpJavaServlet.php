<?php
try {
  if(!extension_loaded("java")) require_once("http://localhost:8080/JavaBridge/java/Java.inc");
  $ctx = java_context();
  $request = $ctx->getServletContext();
  echo $request;
} catch (Exception $e) {
  die("$e");
}
echo "test okay\n";

?>
