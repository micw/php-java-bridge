#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

// fetch classes and compile them to native code.
$Assembly=new MonoClass("System.Reflection.Assembly");
$Assembly->Load("poi");

include ("./excel_antitest.php");
createWorkbook("bench.xls", 200, 200);
?>
