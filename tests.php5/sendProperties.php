#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$h=array("k"=>"v", "k2"=>"v2");
$m=new java("java.util.Properties",$h);
echo $m->size() . " " . java_cast($m->getProperty("k", "ERROR"),"S")." \n";
if(java_values($m->getProperty("k2", "ERROR")) != "v2") {
  echo "ERROR\n";
  exit(1);
}
echo "test okay\n";
exit(0);
?>
