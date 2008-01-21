#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/array.jar");
$array = new java("Array");

$map = $array->getConversion();
$set = $map->entrySet();
$iterator = $set->iterator();
while (java_values($iterator->hasNext())) {
  $next = $iterator->next();
  $key = $next->getKey();
  $value = $next->getValue();
  echo java_values($key)." => ".java_values($value)."\n";
}

echo "\n";

$idx = $array->getIndex("Seelandschaft mit Pocahontas, Arno Schmidt, 1914--1979");
$entry = $array->getEntry($idx);
echo java_values($idx)." => ".java_values($entry)."\n";
?>
