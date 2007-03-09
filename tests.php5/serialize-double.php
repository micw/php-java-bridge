#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$val=123456789123456.789;
$v=new java("java.lang.Double", $val);
echo $val . "\n";
echo $v->doubleValue();
echo "\n";

exit ($v->doubleValue() == $val);
?>
