#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=getcwd();
java_require("$here/noClassDefFound.jar");

$v=new java("NoClassDefFound");
echo $v->s . "\n";

if($v->s=="test okay") {
  exit(0);
} else {
  echo "test failed\n";
  exit(1);
}

?>
