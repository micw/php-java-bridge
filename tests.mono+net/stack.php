#!/usr/bin/php

<?php

if (!extension_loaded('mono')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('mono.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_mono.dll'))) {
    echo "mono extension not installed.";
    exit(2);
  }
}

$s = new Mono('System.Collections.Stack');
$s->Push('Hello World');
$s->Push('Mono');

if ($s->Contains('Mono')) {
	print "Works like a perfect darling\n";
}

print $s->Pop() . ' ' . $s->Pop() . "\n";
?>
