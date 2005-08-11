#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$name=java_get_server_name();

if(!$name) {
  echo "No servers available, please start one.\n";
} else {
  echo "connected to the server: $name\n";
}
?>
