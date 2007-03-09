#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}
/*
 The server log should contain:
 --> <F p="A" />
 <-- <F p="A"/>
An exception occured: php.java.bridge.Request$AbortException
[...]
 --> <F p="E" />
 <-- <F p="E"/>
*/

$count=0;
function toString() {
  global $count;
  $s = new java("java.lang.String", "hello");
  $v = new java("java.lang.String", "hello");
  $t = new java("java.lang.String", "hello");
  $s=$v=$t=null;
  if($count<2) {
    $count++;
    return java_cast(java_closure(), "string");
  }
  echo 'check the server log, it should contain a php.java.bridge.Request$AbortException';
  exit(0);
  return "leaf";
}

echo java_closure();
echo "test failed\n";
exit(1);
?>
