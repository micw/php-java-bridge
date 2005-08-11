#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$sys = new java("java.lang.System");
$sys->setProperty("utf8", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ");
$arr=$sys->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "<br>\n";
}
?>

