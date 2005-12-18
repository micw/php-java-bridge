<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

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
