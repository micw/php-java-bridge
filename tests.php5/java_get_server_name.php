#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$name=java_get_server_name();

if(!$name) {
  echo "No servers available, please start one.\n";
} else {
  echo "connected to the server: $name\n";
}
?>
