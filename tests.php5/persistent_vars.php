#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

include("../php_java_lib/JPersistence.php");
$ser="persist.ser";

// load the session from the disc
if(file_exists($ser)) {
  $file=fopen($ser,"r");
  $id=fgets($file);
  fclose($file);
  try {
    $v=unserialize($id);
  } catch (JavaException $e) {
    echo "ERROR: Could not deserialize: ". $e->getCause() . "\n";
    exit(1);
  }
}

// a new session
if(!$v) {
  echo "creating new session\n";
  $vector=new JPersistenceAdapter(new java("java.lang.StringBuffer", "hello"));
  
  $v=array (
	"test",
	$vector,
	3.14);
  $id=serialize($v);
  $file=fopen($ser,"w");
  fwrite($file, $id);
  fclose($file);
} else {
  echo "cont. session\n";
}
echo $v[0];
echo $v[1];
echo $v[2];
echo "\n";

exit(0);
?>

