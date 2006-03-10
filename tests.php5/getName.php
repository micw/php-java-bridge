#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Thread = new JavaClass("java.lang.Thread");
$name=$Thread->getName();
if("$name" != "java.lang.Thread") {
  echo "ERROR\n";
  exit(1);
 }
echo "test okay\n";
exit(0);

?>
