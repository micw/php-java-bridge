#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$s=new java("java.lang.String", 12);
$c=$s->toCharArray();
if(java_cast($c[0],'integer')==1 && java_cast($c[1],"integer")==2) {
  echo "test okay\n";
  exit(0);
}
else {
  echo "ERROR\n";
  exit(1);
}
?>
