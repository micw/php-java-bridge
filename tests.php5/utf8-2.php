#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

// Start server with:
// java -Dfile.encoding=ASCII -jar JavaBridge.jar INET:0 4 ""

// test the default UTF-8 encoding for arrays

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
java_set_library_path("$here/arrayToString.jar");
$ArrayToString = new JavaClass("ArrayToString");
$ar=array("Cześć!", " שלום", " Grüß Gott", " Dobrý deň", " Dobrý den", " こんにちは, ｺﾝﾆﾁﾊ");
print $ArrayToString->arrayToString($ar) . "\n";

?>
