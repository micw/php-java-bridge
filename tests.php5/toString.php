#!/usr/bin/php
<?php

if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Object = new java_class ("java.lang.Object");
$ObjectC = new JavaClass ("java.lang.Object");
$object = $Object->newInstance();

// test __toString()
// should display "class java.lang.Object"
echo $Object; echo "\n";

// test cast to string
// should display "class java.lang.Object"
echo "" . $Object->__toString() . "\n";
echo "" . $ObjectC->__toString() . "\n";

echo $object->__toString()."\n";
?>
