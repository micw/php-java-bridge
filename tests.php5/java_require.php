#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
 }

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();

//$s = new java("java.lang.String", "hello");
try {
  java_require("$here/cachex.jar");
  echo "test failed\n";
  exit(1);
} catch (JavaException $ex) {
  $IOException = new JavaClass("java.io.IOException");
  $cause = $ex->getCause();
  if(!java_instanceof($cause, $IOException)) {
    echo "test failed\n";
    exit(3);
  }
} catch (Exception $e) {
  echo "test failed: $e\n";
  exit(2);
}

try {
  java_require("$here/cache.jar");
} catch (Exception $ex) {
  echo "test failed\n";
  exit(4);
}
$Cache = new JavaClass("Cache");
echo "test okay\n";
exit(0);

?>

