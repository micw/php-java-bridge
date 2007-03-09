#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$class = new java_class("java.lang.Class");
$arr = java_get_values($class->getConstructors());
if(0==sizeof($arr)) {
     echo "test okay\n";
     exit(0);
}
echo "error\n";
exit(1);

?>
