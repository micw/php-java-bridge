#!/usr/bin/php
if (!extension_loaded('java')) {
  echo "Please permanently activate the extension. Loading java extension now... ";
  if (!dl('java.so')&&!dl('java.dll')) {
    echo "java extension not installed.";
    exit;
  }
}

$v = new java("java.lang.System");
$arr=$v->getProperties();
foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "\n";
}
?>
