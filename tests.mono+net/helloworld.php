#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$Console = new Mono('System.Console');
$Console->WriteLine("Hello World");
?>
