<?php
java_set_library_path("cache.jar");
$Cache = new JavaClass("Cache");
$instance= $Cache->getInstance();
echo $instance;
?>

