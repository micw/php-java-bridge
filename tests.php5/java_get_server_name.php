#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
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
