#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$s = new JavaClass("java.lang.System");
if(!$s) die("test failed1\n");
// check null proxy
java_begin_document();
$k = $s->gc();
java_end_document();
echo $k; echo "\n";
// null tests work since PHP 5.1.4 and above
if (extension_loaded('java') && version_compare("5.1.4", phpversion(), "<=")) {
  if($k) die("test failed2\n");
  if($k==null) die("test failed2\n");
 }
if(is_null($k)) die("test failed3\n");

echo "test okay\n";
exit(0);
