#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$ar=array(1, 2, 3);
$v=new java("java.util.Vector", $ar);
$Arrays=new java_class("java.util.Arrays");
$l=$Arrays->asList($ar); 
echo $l->size() . " " . $v->capacity();
echo "\n";
?>
