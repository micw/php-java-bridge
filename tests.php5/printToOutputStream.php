#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$out = new java("java.io.ByteArrayOutputStream");
$stream = new java("java.io.PrintStream", $out);
$stream->print("This is a test.");
echo "Stream: " . $out . "\n";
echo "Stream as binary data: " . $out->toByteArray() . "\n";
echo "Stream as UTF-8 string: " . $out->toString() . "\n";

?>