#!/usr/bin/php

<?php
require_once ("java/Java.inc");

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

