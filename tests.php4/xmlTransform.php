#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$j_tfClass = new java_class("javax.xml.transform.TransformerFactory");
$j_tf = $j_tfClass->newInstance();

?>
