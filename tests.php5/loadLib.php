#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=getcwd();
$ext=trim(`php-config --extension-dir`);
if(!file_exists("$ext/lib")) {
  mkdir("$ext/lib");
}
if(file_exists("$ext/lib/array/array.jar")) {
  unlink("$ext/lib/array/array.jar");
}
if(file_exists("$ext/lib/array")) {
  rmdir("$ext/lib/array");
}
mkdir("$ext/lib/array");
copy("$here/array.jar", "$ext/lib/array/array.jar");
try {
  java_set_library_path("array/array.jar");
  $testvar = new Java('Array');
  echo "Test okay\n";
  exit(0);
} catch (Exception $e) {
  echo "Exception: " . $e . "\n";
  exit(1);
}
?>
