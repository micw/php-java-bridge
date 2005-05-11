#!/usr/bin/php

# start 3 backends with:
# modules/RunJavaBridge INET:0 5 ""
# * test upper bound at 12 (start more than 36 clients)
# * test if load balancer spreads the requests

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

echo "Starting client.\n";
$Thread = new java_class("java.lang.Thread");
$Thread->sleep(10000);
echo "client terminated\n";

?>

