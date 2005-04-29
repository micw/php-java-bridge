#!/usr/bin/php

<?php

// Start server with:
// java -Dfile.encoding=ASCII -jar JavaBridge.jar INET:0 4 ""

// test the default UTF-8 encoding for arrays

$here=trim(`pwd`);
java_set_library_path("$here/arrayToString.jar");
$ArrayToString = new JavaClass("ArrayToString");
$ar=array("Cześć!", " שלום", " Grüß Gott", " Dobrý deň", " Dobrý den", " こんにちは, ｺﾝﾆﾁﾊ");
print $ArrayToString->arrayToString($ar) . "\n";

?>