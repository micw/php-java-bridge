#!/usr/bin/php

<?php
require_once ("java/Java.inc");

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
