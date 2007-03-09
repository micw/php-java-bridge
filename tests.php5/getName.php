#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Thread = new JavaClass("java.lang.Thread");
$name=java_values($Thread->getName());
if("$name" != "java.lang.Thread") {
  echo "ERROR\n";
  exit(1);
 }
echo "test okay\n";
exit(0);

?>
