<?php
if(!extension_loaded("java"))
   require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc");

$here=getcwd();
try {
  java_require("$here/testCoerceArray.jar");
  $t1 = new java("TestCoerceArray");
  $t2 = new java("TestCoerceArray");
  echo java("TestCoerceArray")->f(array($t1, $t2)); echo "\n";
} catch (Exception $e) {
  echo "test failed\n";
  exit (1);
}
exit(0);

?>
