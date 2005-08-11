#!/usr/bin/php

<?php

if (!extension_loaded('mono')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('mono.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_mono.dll'))) {
    echo "mono extension not installed.";
    exit(2);
  }
}

$Console = new Mono('System.Console');
$Console->WriteLine('Varargs:{0} {1} {2} {3} {4} {5} {6}', array('hello', 'world', ', this', 'is', 'a', 'big', 'test!'));
?>
