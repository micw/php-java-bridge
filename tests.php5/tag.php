<?php
require_once("http://localhost:8080/JavaBridge/java/Java.inc");
include("../php_java_lib/JspTag.php");

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_require("$here/fooTag.jar");

$attributes = array("att1"=>"98.5", "att2"=>"92.3","att3"=>"107.7");
$pc = new java_PageContext();
$tag = new java_Tag($pc, "FooTag", $attributes);
if($tag->start()) {
  do {
    $pc->getPageContext()->getOut()->print("member:: ");
    $pc->getPageContext()->getOut()->println($pc->getPageContext()->getAttribute("member"));
  } while($tag->repeat());
}
$tag->end();
?>
