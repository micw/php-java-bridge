#!/usr/bin/php

<?php
require_once ("java/Java.inc");

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_require("$here/binaryData.jar");
$binaryData = new Java("BinaryData");

echo java_inspect($binaryData)

?>
