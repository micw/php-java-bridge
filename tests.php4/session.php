<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
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

$session->put("a", $session->get("a")+1);
$session->put("b", $session->get("b")-1);

$val=$session->get("a");
$c=$session->get("c");
if($c!=null) {echo "test failed"; exit(1);}
echo "session var: $val\n";

if($session->get("b")==0) $session->destroy();

?>
