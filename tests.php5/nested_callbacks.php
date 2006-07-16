#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
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
    return java_closure() . "$c";
  }
  return "leaf";
}

$res=java_closure();
if("$res" != "leaf9876543210") {
  echo "test failed\n";
  exit(1);
 }
if("$res" != "leaf") {
  echo "test failed\n";
  exit(2);
 }
echo $res; echo "\n";
$count=0; 
echo "$res\n";
echo "test okay\n";
exit(0);

?>
