#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$Console = new Mono('System.Console');
$Console->WriteLine('Varargs:{0} {1} {2} {3} {4} {5} {6}', array('hello', 'world', ', this', 'is', 'a', 'big', 'test!'));
?>
