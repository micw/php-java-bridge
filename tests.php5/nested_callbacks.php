#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$count=0;
function toString() {
  global $count;
  $s = new java("java.lang.String", "hello");
  $v = new java("java.lang.String", "hello");
  $t = new java("java.lang.String", "hello");
  $s=$v=$t=null;
  if($count<10) {
    $c = $count++;
    return java_cast(java_closure(), "String") . "$c";
  }
  return "leaf";
}

$res=java_closure();
if(java_cast($res, "S") != "leaf9876543210") {
  echo "test failed\n";
  exit(1);
 }

if(java_cast($res, "S") != "leaf") {
  echo "test failed\n";
  exit(2);
 }
echo java_cast($res, "S"); echo "\n";
$count=0; 
echo java_cast($res, "S")."\n";
echo "test okay\n";
exit(0);

?>
