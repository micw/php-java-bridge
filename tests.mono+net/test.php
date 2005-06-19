#!/usr/bin/php
<?php
if (!extension_loaded('mono')) {
  if ((version_compare("5.0.0", phpversion(), "<=")) && (version_compare("5.0.4", phpversion(), ">"))) {
    echo "This PHP version does not support dl().\n";
    echo "Please add an extension=mono.so or extension=php_mono.dll entry to your php.ini file.\n";
    exit(1);
  }

  echo "Please permanently activate the extension. Loading mono extension now...\n";
  if (!dl('mono.so')&&!dl('php_mono.dll')) {
    echo "mono extension not installed.";
    exit(2);
  }
}
phpinfo();
print "\n\n";

$v = new Mono("java.lang.System");
$arr=$v->getProperties();

foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "<br>\n";
}
?>

