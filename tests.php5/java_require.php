#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
 }

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();

try {
  java_require("$here/cachex.jar");
  echo "test failed\n";
  exit(1);
} catch (JavaException $ex) {
  $IOException = new JavaClass("java.io.IOException");
  $cause = $ex->getCause();
  if(!java_instanceof($cause, $IOException)) {
    echo "test failed\n";
    exit(3);
  }
} catch (Exception $e) {
  echo "test failed: $e\n";
  exit(2);
}

try {
  java_require("$here/cache.jar");
} catch (Exception $ex) {
  echo "test failed\n";
  exit(4);
}
$Cache = new JavaClass("Cache");
echo "test okay\n";
exit(0);

?>

