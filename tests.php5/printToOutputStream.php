#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
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
echo "Stream: " . $out->__toString() . "\n";
echo "Stream as $file_encoding string: ".java_values($out->toString())."\n";
echo "Stream as binary data: ".java_cast($out->toByteArray(),"S")."\n";

?>
