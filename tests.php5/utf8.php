#!/usr/bin/php
<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$sys = new java("java.lang.System");
$sys->setProperty("utf8", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ");
$arr=$sys->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "<br>\n";
}
?>

