<?php

function check($name) {
  $len = strlen($name);
  return strncasecmp(php_uname(), $name, $len) == 0;
}
function appendIncludeDir($dir) {
  $append = check("windows")?";$dir":":$dir";
  ini_set("include_path", ini_get("include_path").$append);
}
appendIncludeDir(".."); require_once("java/Java.inc");

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
