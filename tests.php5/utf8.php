#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$sys = new java("java.lang.System");
$sys->setProperty("utf8", "Cześć! -- שלום -- Grüß Gott -- Dobrý deň -- Dobrý den -- こんにちは, ｺﾝﾆﾁﾊ");
$arr=$sys->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  java_values($value) . "<br>\n";
}
?>

