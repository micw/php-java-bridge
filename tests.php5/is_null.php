#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$s = new JavaClass("java.lang.System");
if(!$s) die("test failed1\n");
// check null proxy
java_begin_document();
$k = $s->gc();
java_end_document();
echo $k; echo "\n";
if($k) die("test failed2\n");
if(is_null($k)) die("test failed3\n");

echo "test okay\n";
exit(0);