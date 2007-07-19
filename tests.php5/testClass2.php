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
java_require("$here/testClass.jar");

/* See PR1616498 */
try {
  $obj = new Java("TestClass");
  $cls = $obj->getClass();
  $name = $cls->getName();
  $objname = $obj->getName(); //this fails in 3.1.8 due to a cache problem
} catch (JavaException $e) {
  echo "test failed";
  exit(1);
}
echo "test okay\n";
exit(0);
?>
