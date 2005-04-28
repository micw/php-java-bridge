#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}
$string = new Java("java.lang.String", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ");
print $string->toString() . "\n";

java_set_file_encoding("UTF-8");

#  $string = new Java("java.lang.String", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ", "UTF-8");
#  print $string->toString() . "\n";

#  $here=trim(`pwd`);
#  java_set_library_path("$here/arrayToString.jar");
#  $ArrayToString = new JavaClass("ArrayToString");
#  $ar=array("Cześć!", " שלום", " Grüß Gott", " Dobrý deň", " Dobrý den", " こんにちは, ｺﾝﾆﾁﾊ");
#  print $ArrayToString->arrayToString($ar) . "\n";

?>
