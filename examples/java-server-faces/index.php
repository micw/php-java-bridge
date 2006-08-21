<?php

require_once("java/Java.php");

  // redirect to backend.
function redirect() {
  $target=dirname($_SERVER['PHP_SELF']);
  $backend=java_server_name();
  if(!$backend) die("Servlet engine not running");

  $array=split(":", $backend);
  $port=$array[1];
  header ("Location: http://$_SERVER[SERVER_NAME]:$port$target/helloWorld.jsf");
}

redirect();

?>
