#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here = trim(`pwd`);
$java_output = "workbook_java.xls";
$php_output = "workbook_php.xls";

$sys = new java("java.lang.System");

// fetch classes and compile them to native code.
java_set_library_path("$here/exceltest.jar;http://php-java-bridge.sf.net/poi.jar");
$excel = new java("ExcelTest");
$excel->createWorkbook("/dev/null");

// test starts
$start = $sys->currentTimeMillis();
$excel = new java("ExcelTest");
$excel->createWorkbook("$here/$java_output");
$t_java = $sys->currentTimeMillis() - $start;

include("$here/excel_antitest.php");
$start = $sys->currentTimeMillis();
createWorkbook("$here/$php_output");
$t_php = $sys->currentTimeMillis() - $start;

echo "Created excel file $java_output via compiled java in $t_java ms.\n";
echo "Created excel file $php_output via interpreted java reflection calls in $t_php ms.\n";

?>