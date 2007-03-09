#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/array6.jar");

$testvar = new Java('Array6');
$testobj = $testvar->test();
echo $testobj[0][0][0][0][0][1];
echo $testobj[0][0][0][0][1][0];
echo $testobj[0][0][0][1][0][0];
echo $testobj[0][0][1][0][0][0];
echo $testobj[0][1][0][0][0][0];
echo $testobj[1][0][0][0][0][0];
echo "\n";
?>
