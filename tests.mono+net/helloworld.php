#!/usr/bin/php

<?php

if(!extension_loaded('mono')) {
  dl('mono.' . PHP_SHLIB_SUFFIX);
}

$Console = new Mono('System.Console');
$Console->WriteLine("Hello World");
?>
