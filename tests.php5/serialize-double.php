#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$val=1234523456.789;
$v=new java("java.lang.Double", $val);
echo $val . "\n";
echo $v->doubleValue();
echo "\n";

java_values($v->doubleValue()) == $val or die ("test failed");
exit (0);
?>
