#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=getcwd();
java_set_library_path("$here/numberTest.jar");
	
$test = new java('NumberTest');
print "getInteger() = " . $test->getInteger() . "<br>\n";
print "getPrimitiveInt() = " . $test->getPrimitiveInt() . "<br>\n";
print "getFloat() = " . $test->getFloat() . "<br>\n";
print "getPrimitiveFloat() = " . $test->getPrimitiveFloat() . "<br>\n";
print "getDouble() = " . $test->getDouble() . "<br>\n";
print "getPrimitiveDouble() = " . $test->getPrimitiveDouble() . "<br>\n"; 
print "getBigDecimal() = " . $test->getBigDecimal() . "<br>\n";

?>
