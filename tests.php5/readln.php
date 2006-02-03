<?php
dl('java.so');
function readln() {
  $byte = JavaClass("java.lang.Byte")->TYPE;
  $byteArray = JavaClass("java.lang.reflect.Array")->newInstance($byte, 255);
  $length = JavaClass("java.lang.System")->in->read($byteArray);

  if($length>=0) {
    $result = new java("java.lang.String", $byteArray, 0, $length);
    return "$result";
  }
  return "error!";
}

echo readln();

?>
