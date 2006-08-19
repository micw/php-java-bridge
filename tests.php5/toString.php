#!/usr/bin/php
<?php

if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Object = new java_class ("java.lang.Object");
$ObjectC = new JavaClass ("java.lang.Object");
$object = $Object->newInstance();

// test __toString()
// should display "class java.lang.Object"
echo $Object; echo "\n";

// test cast to string
// should display "class java.lang.Object"
echo "" . $Object->__toString() . "\n";
echo "" . $ObjectC->__toString() . "\n";

echo $object->__toString()."\n";
?>
