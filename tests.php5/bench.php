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
$excel->createWorkbook("/dev/null", 1, 1);

// test starts
$start = $sys->currentTimeMillis();
$excel = new java("ExcelTest");
$excel->createWorkbook("$here/$java_output", 200, 200);
$t_java = $sys->currentTimeMillis() - $start;

include("$here/excel_antitest.php");
$start = $sys->currentTimeMillis();
createWorkbook("$here/$php_output", 200, 200);
$t_php = $sys->currentTimeMillis() - $start;

echo "Created excel file $java_output via compiled java in $t_java ms.\n";
echo "Created excel file $php_output via interpreted PHP and java reflection calls in $t_php ms.\n";

/*
       java class             php function           java class      php function
       interpreted (-Xint)    interpreted (-Xint)    compiled        compiled
jdk
1.4:   13367 ms               42919 ms               2325 ms         22276 ms
1.5:   18342 ms               42048 ms               2227 ms         21008 ms
GNU:   36348 ms               73440 ms                 -                -

  -> jni/reflection overhead ca. 2.0 (GNU) .. 3.1 (JDK 1.4)
  
*/
?>
