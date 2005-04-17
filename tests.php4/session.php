<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$session=java_get_session("testSession", array("a" => 1, "b" => 5));

$session->put("a", $session->get("a")+1);
$session->put("b", $session->get("b")-1);

if($session->isNew()) {
  echo "new session\n";
}
else {
  echo "cont session\n";
}
     
$val=$session->get("a");
echo "session var: $val\n";

if($session->get("b")==0) $session->destroy();

?>
