#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}
function x1() {
  echo("x1 called\n");
  return true;
}
$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_require("$here/../tests.php5/callback.jar");

$closure=java_closure(null, "x1");
$callbackTest=new java('Callback$Test', $closure);

if($callbackTest->test()) {
  echo "test okay\n";
  exit(0);
} else {
  echo "test failed\n";
  exit(1);
}
?>
