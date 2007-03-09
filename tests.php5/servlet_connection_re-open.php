#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}
ini_set("max_execution_time", 0);
if($argc<2) {
  echo "No automatic test. Use php swing-button.php --force to run this test.\n";
  exit(0);
 }

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/arrayArray.jar");
$Array = new java_class("ArrayArray");
$n=60;
$arrayArray=$Array->create($n);

// Keep-Alive default is 15s, the following tests if the client
// re-opens the connection (the test will dump core if not).
$String=new java_class("java.lang.String");
for($i=0; $i<$n; $i++) {
	$ar = $arrayArray[$i]->array;
	echo $n-$i-1 . " ";
	sleep(1);
}

echo "\n";

?>
