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

// must succeed
echo "must succeed\n";
java_require("$here/noClassDefFound.jar;$here/doesNotExist.jar");
$v=new java("NoClassDefFound");
$v->call(null);

?>
