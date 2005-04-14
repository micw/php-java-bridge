#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if (version_compare("5.0.0", phpversion(), "<=")) {
    echo "This PHP version does not support dl().\n";
    echo "Please add an extension=java.so or extension=php_java.dll entry to your php.ini file.\n";
    exit(1);
  }

  echo "Please permanently activate the extension. Loading java extension now...\n";
  if (!dl('java.so')&&!dl('php_java.dll')) {
    echo "java extension not installed.";
    exit(2);
  }
}
//phpinfo();
//print "\n\n";

$v = new java("java.lang.System");
$arr=$v->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "<br>\n";
}
?>

