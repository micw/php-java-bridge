#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  // extension not activated in global php.ini
  // file, try to load it now
  if (!dl('java.so')&&!dl('java.dll')) {
    exit;
  }
}

$v = new java("java.lang.System");
$arr=$v->getProperties();
foreach ($arr as $key => $value) {
		print $key . " -> " .  $value . "\n";
}
?>
