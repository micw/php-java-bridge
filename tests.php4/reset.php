#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

echo "first invocation:\n";
system("php -q cache.php");
echo "second invocation:\n";
system("php -q cache.php");

echo "calling java_reset() to clear all caches\n";
java_reset();

echo "first invocation after reset:\n";
system("php -q cache.php");
echo "second invocation after reset:\n";
system("php -q cache.php");
?>

