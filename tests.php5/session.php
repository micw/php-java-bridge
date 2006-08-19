<?php

if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$session=java_get_session("testSession");
if($session->isNew()) {
  echo "new session\n";
  $session->put("a", 1);
  $session->put("b", 5);
  $session->put("c", null);
}
else {
  echo "cont session\n";
}

$session->put("a", java_values($session->get("a"))+1);
$session->put("b", java_values($session->get("b"))-1);

$val=$session->get("a");
$c=$session->get("c");
if($c!=null) {echo "test failed"; exit(1);}
echo "session var: ".java_values($val)."\n";

if(java_values($session->get("b"))==0) $session->destroy();

?>
