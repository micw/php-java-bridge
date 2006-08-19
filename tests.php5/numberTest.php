#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    require_once("java/Java.php");
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_require("$here/numberTest.jar");
	
$test = new java('NumberTest');
print "getInteger() = "; echo($test->getInteger()); echo "<br>\n";
print "getPrimitiveInt() = "; echo($test->getPrimitiveInt()); echo "<br>\n";
print "getFloat() = "; echo($test->getFloat()); echo "<br>\n";
print "getPrimitiveFloat() = "; echo($test->getPrimitiveFloat()); echo "<br>\n";
print "getDouble() = "; echo($test->getDouble()); echo "<br>\n";
print "getPrimitiveDouble() = "; echo($test->getPrimitiveDouble()); echo "<br>\n"; 
print "getBigDecimal() = "; echo($test->getBigDecimal()); echo "<br>\n";

?>
