<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/binaryData.jar");

$binaryData = new java("BinaryData");
$data = $binaryData->getData(700*1024);
for($i=0; $i < 10; $i++) {
  $data=$binaryData->compare($data);
  $str1=substr($data, 255, 256);
}
$str='&;a&amp;&quote"&quot;&&&;;"';
$binaryData->b='&;a&amp;&quote"&quot;&&&;;"';
$binaryData->compare('&;a&amp;&quote"&quot;&&&;;"');
if($str!=$binaryData->toString()) { echo "ERROR\n"; exit(1); }

$data = $binaryData->getData(1024);
$s1=substr($binaryData->toString(), 0, 256);
$binaryData->b=$str1;
$s2=substr($binaryData->toString(), 0, 256);

if($s1!=$s2) { echo "ERROR\n"; exit(2); }
echo "test ok\n";
exit(0);

?>
