#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/numberTest.jar");
	
$test = new java('NumberTest');
print "getInteger() = "; var_dump($test->getInteger()); echo "<br>\n";
print "getPrimitiveInt() = "; var_dump($test->getPrimitiveInt()); echo "<br>\n";
print "getFloat() = "; var_dump($test->getFloat()); echo "<br>\n";
print "getPrimitiveFloat() = "; var_dump($test->getPrimitiveFloat()); echo "<br>\n";
print "getDouble() = "; var_dump($test->getDouble()); echo "<br>\n";
print "getPrimitiveDouble() = "; var_dump($test->getPrimitiveDouble()); echo "<br>\n"; 
print "getBigDecimal() = "; var_dump($test->getBigDecimal()); echo "<br>\n";

?>
