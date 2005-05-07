#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
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
  echo "Test okay";
  exit(0);
} catch (Exception $e) {
  echo "Exception: " . $e;
  exit(1);
}
?>
