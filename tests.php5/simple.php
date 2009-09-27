<?php
  // Remove all entries from the php.ini and call this twice, with and
  // w/o java.so
require_once("java/Java.inc");
echo java_server_name();
?>
