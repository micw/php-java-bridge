#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Array = new JavaClass("java.lang.reflect.Array");
$testobj=$Array->newInstance(new JavaClass("java.lang.String"), array(2, 2, 2, 2, 2, 2));

$testobj[0][0][0][0][0][1] = 1;
$testobj[0][0][0][0][1][0] = 2;
$testobj[0][0][0][1][0][0] = 3;
$testobj[0][0][1][0][0][0] = 4;
$testobj[0][1][0][0][0][0] = 5;
$testobj[1][0][0][0][0][0] = 6;

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
java_require("$here/array6.jar");
$array6 = new java("Array6");
$success = $array6->check($testobj);
var_dump(java_values($testobj[0][0][0][0]));
if(!$success) {
  echo "ERROR\n";
  exit(1);
}
echo "test okay\n";
exit(0);
?>
