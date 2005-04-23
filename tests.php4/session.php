<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$session=java_get_session("testSession");;
if($session->isNew()) {
  echo "new session\n";
  $session->put("a", 1);
  $session->put("b", 5);
}
else {
  echo "cont session\n";
}

$session->put("a", $session->get("a")+1);
$session->put("b", $session->get("b")-1);
     
$val=$session->get("a");
echo "session var: $val\n";

if($session->get("b")==0) $session->destroy();

?>
