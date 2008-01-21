<?php
try {
  if(!extension_loaded("java")) require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc");
  $ctx = java_context();
  $request = $ctx->getServletContext();
  echo $request;
} catch (Exception $e) {
  die("$e");
}
echo "test okay\n";

?>
