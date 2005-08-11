#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$file_encoding="ASCII";
java_set_file_encoding($file_encoding);

$out = new java("java.io.ByteArrayOutputStream");
$stream = new java("java.io.PrintStream", $out);
$str = new java("java.lang.String", "Cześć! -- שלום -- Grüß Gott", "UTF-8");

$stream->print($str);
echo "Stream: " . $out . "\n";
echo "Stream as $file_encoding string: " . $out->toString() . "\n";
echo "Stream as binary data: " . $out->toByteArray() . "\n";

?>
