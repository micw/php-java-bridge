<?php
//java_set_library_path("cache.jar");
$Cache = new JavaClass("Cache");
//$instance=$Cache->getInstance(); //instance will stay the VM until the VM runs out of memory
echo $Cache->hashCode();
echo $Cache->hashCode();
?>

