#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  // extension not activated in global php.ini
  // file, try to load it now
  if (!dl('java.so')&&!dl('java.dll')) {
    exit;
  }
}

// were to load libraries from (example):
java_set_jar_library_path("http://somewhere.org/lib1.jar;file:c:/lib2.jar");

$v = new java("java.lang.System");
$arr=$v->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "\n";
}
?>
