#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
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

