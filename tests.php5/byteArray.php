#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$s=new java("java.lang.String", 12);
$c=$s->getBytes("ASCII");
if(java_values($c[0])==ord('1') && java_values($c[1])==ord('2')) {
  echo "test okay\n";
  exit(0);
}
else {
  echo "ERROR\n";
  exit(1);
}
?>
