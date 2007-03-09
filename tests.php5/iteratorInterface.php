#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$conversion = new  java("java.util.Properties");
$conversion->put("long", "java.lang.Byte java.lang.Short java.lang.Integer");
$conversion->put("boolean", "java.lang.Boolean");
$conversion->put("double", "java.lang.Double");
$conversion->put("null", "null");
$conversion->put("object", "depends");
$conversion->put("array of longs", "int[]");
$conversion->put("array of doubles", "double[]");
$conversion->put("array of boolean", "boolean[]");
$conversion->put("mixed array", "");
foreach ($conversion as $key=>$value) {               
  echo "$key => ".java_cast($value, "S")."\n";
}
?>
