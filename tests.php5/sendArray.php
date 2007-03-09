#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

/*
 * Return php value(s) for the java object
 */
function getPhpValues($ob) {
  if($ob instanceof Java) return java_values($ob);
  return $ob;
}


$ar=array(1, 2, 3, 5, 7, 11, -13, -17.01, 19);
unset($ar[1]);
$v=new java("java.util.Vector", $ar);
$Arrays=new java_class("java.util.Arrays");
$l=$Arrays->asList($ar); 
$v->add(1, null);
$l2 = $v->sublist(0,$v->size());

echo java_cast($l, "S")."\n".java_cast($l2,"S")."\n";
$res1 = java_values($l);
$res2 = java_values($l2);
$res3 = array();
$res4 = array();
$i=0;

foreach($v as $key=>$val) {
  $res3[$i++]=getPhpValues($val);
}
for($i=0; $i<$l2->size(); $i++) {
  $res4[$i]=getPhpValues($l2[$i]);
}

if(!$l->equals($l2)) {
  echo "ERROR\n";
  exit(1);
}
if($l[1] != null || $res3 != $res1 || $res4 != $res1) {
  echo "ERROR\n";
  exit(2);
}

echo "test okay\n";
exit(0);
?>
