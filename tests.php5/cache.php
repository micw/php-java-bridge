#!/usr/bin/php

<?php
require_once ("java/Java.inc");

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/cache.jar");
$Cache = new JavaClass("Cache");
$instance= $Cache->getInstance();
echo $instance->hashCode();
?>

