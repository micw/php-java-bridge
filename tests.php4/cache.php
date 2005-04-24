<?php
$here=trim(`pwd`);
java_set_library_path("$here/cache.jar");
$Cache = new JavaClass("Cache");
$instance= $Cache->getInstance();
echo $instance->hashCode();
?>

