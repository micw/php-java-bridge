#!/usr/bin/php

<?php
require_once ("java/Java.inc");

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/showResources.jar");
$sr=new java("ShowResources");
$sr->main(array());
echo "\n\n";

$sr->main(array("showResources.jar"));
?>
