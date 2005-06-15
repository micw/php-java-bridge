#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_set_library_path("$here/showResources.jar;$here/array6.jar;$here/cache.jar");
$sr=new java("ShowResources");
$sr->main(array());
echo "\n\n";

$sr->main(array("cache.jar"));
$Cache = new JavaClass("Cache");
$instance= $Cache->getInstance();
echo $instance->hashCode();

?>
