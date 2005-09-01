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
print 'getInteger() = ' . $test->getInteger() . '<br>';
print 'getPrimitiveInt() = ' . $test->getPrimitiveInt() . '<br>';	
print 'getFloat() = ' . $test->getFloat() . '<br>';
print 'getPrimitiveFloat() = ' . $test->getPrimitiveFloat() . '<br>';	
print 'getDouble() = ' . $test->getDouble() . '<br>';
print 'getPrimitiveDouble() = ' . $test->getPrimitiveDouble() . '<br>';	
print 'getBigDecimal() = ' . $test->getBigDecimal() . '<br>';
	
/* MY OUTPUT:
	getInteger() = -20
	getPrimitiveInt() = -20
	getFloat() = 0
	getPrimitiveFloat() = 0
	getDouble() = 0
	getPrimitiveDouble() = 0
	getBigDecimal() = 0
*/
