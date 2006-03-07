#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$string = new Java("java.lang.String", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ");
print $string->toString() . "\n";

?>
