#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$h=array("k"=>"v", "k2"=>"v2");
$m=new java("java.util.Properties",$h);
echo $m->size() . " " . $m->getProperty("k", "ERROR") . " \n";
if($m->getProperty("k2", "ERROR") != "v2") {
  echo "ERROR\n";
  exit(1);
}
echo "test okay\n";
exit(0);
?>
