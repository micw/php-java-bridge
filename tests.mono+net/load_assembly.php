#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}
$Assembly=new MonoClass("System.Reflection.Assembly");
$Assembly->Load("sample_lib");

$hello=new Mono("sample.hello");
echo $hello->World("world") . "\n";

?>
