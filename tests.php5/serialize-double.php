#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$val=123456789123456.789;
$v=new java("java.lang.Double", $val);
echo $val . "\n";
echo $v->doubleValue();
echo "\n";

exit ($v->doubleValue() == $val);
?>
