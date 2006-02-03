#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here = getcwd();
$java_output = "workbook_java.xls";
$php_output = "workbook_php.xls";

$sys = new java("java.lang.System");

// fetch classes and compile them to native code.
// use local poi.jar, if installed
java_require("$here/exceltest.jar;poi.jar");
java_require("$here/exceltest.jar;http://php-java-bridge.sf.net/poi.jar");
$excel = new java("ExcelTest");
$excel->createWorkbook("/dev/null", 1, 1);

// test starts
$sys->gc();
$start = $sys->currentTimeMillis();
$excel = new java("ExcelTest");
$excel->createWorkbook("$here/$java_output", 200, 200);
$sys->gc();
$t_java = $sys->currentTimeMillis() - $start;

include("$here/excel_antitest.php");
$sys->gc();
$start = $sys->currentTimeMillis();
createWorkbook("$here/$php_output", 200, 200);
$sys->gc();
$t_php = $sys->currentTimeMillis() - $start;

echo "Created excel file $java_output via compiled java in $t_java ms.\n";
echo "Created excel file $php_output via interpreted PHP in $t_php ms. (" . $t_php/$t_java .")\n";

/*
Sample results on a 1.4GHZ i686, kernel 2.6.8

--------------------------------------------------
PHP/Java Bridge Version 1.0.8:
       pure java              mix PHP/Java           pure java     mix PHP/Java
       interpreted (-Xint)    interpreted (-Xint)    compiled        compiled
jdk
1.4:   13367 ms               42919 ms               2325 ms         22276 ms
1.5:   18342 ms               42048 ms               2227 ms         21008 ms
GNU:   36348 ms               73440 ms                 -                -
  -> jni/net/reflection overhead ca. 2.0 (GNU) .. 3.1 (JDK 1.4)


--------------------------------------------------
PHP/Java Bridge Version 2.0.7:
jdk
1.4:   13929 ms               39723 ms               2349 ms         9232 ms
GNU:   36365 ms               54082 ms                 -                -
  -> net/reflection overhead ca. 1.5 (GNU) .. 2.85 (JDK 1.4)

Note that the compiled PHP/Java mix is now 2.28 times faster (compared
to PHP/Java Bridge version 1.0.8). Because we don't use the java
native interface ("JNI") anymore, our high-level XML protocol code
uses fewer round-trips, avoids expensive JNI lookups and can be JIT
compiled.

*/
?>
