#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$string = new Java("java.lang.String", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ", "UTF-8");
print $string->toString();

?>
