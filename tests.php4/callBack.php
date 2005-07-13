#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_require("$here/callBack.jar");

class impl {// methods are called by index#, so their name is irrelevant.
    function passX($Object) { echo $Object->hashCode(); return $Object; }
    function pass2($o) { return $o; }
    function pass3($s) { echo "pass3: $s\n"; }
    function pass4() { echo "pass4\n"; }
    function pass5($s, $a, $b, $c, $d) { return array($s, $a, $b, $c, $d); }
}
$impl = new impl();
$sig = new java("CallBack");
$closure = java_closure($impl, $sig);
$test = new java('CallBack$Impl', $closure);

$x=$test->test($sig);
echo $x->hashCode();
var_dump($test->test(array(1, 2, 3)));
$test->test("hello");
$test->test();
var_dump($test->test("hello", 1, true, 3, -998876));

?>
