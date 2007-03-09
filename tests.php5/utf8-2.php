#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

// Start server with:
// java -Dfile.encoding=ASCII -jar JavaBridge.jar INET:0 4 ""

// test the default UTF-8 encoding for arrays

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/arrayToString.jar");
$ArrayToString = new JavaClass("ArrayToString");
$ar=array("Cześć!", " שלום", " Grüß Gott", " Dobrý deň", " Dobrý den", " こんにちは, ｺﾝﾆﾁﾊ");
print java_values($ArrayToString->arrayToString($ar)) . "\n";

?>
