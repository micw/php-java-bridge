<?php

if(!extension_loaded("java"))
   require_once("http://localhost:8080/JavaBridge/java/Java.inc");

$here=getcwd();
java_require("$here/testClosureCache.jar");
function toString() {return "ccl";}
$tc = new Java("TestClosureCache");
"ichild::ccl"==$tc->proc1(java_closure(null, null, java('TestClosureCache$IChild'))) ||die(1);
"iface::ccl"==$tc->proc1(java_closure(null, null, java('TestClosureCache$IFace'))) || die(2);
"object::ccl"==$tc->proc1(java_closure(null, null, null)) || die(3);
"object::ccl"==$tc->proc1(java_closure()) || die(4);
echo "test okay\n";
?>
