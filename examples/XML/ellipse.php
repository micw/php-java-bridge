#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

// create a svg picture with an ellipse in it
$FactoryClass = new JavaClass("javax.xml.parsers.DocumentBuilderFactory");
$factory = $FactoryClass->newInstance();

$builder = $factory->newDocumentBuilder();

$myDocument = $builder->newDocument();
$svgElement = $myDocument->createElementNS("http://www.w3.org/2000/svg", "svg");
$myDocument->appendChild($svgElement);
$svgElement->setAttribute("width", "4cm");
$svgElement->setAttribute("height", "8cm");

$ellipseElement = $myDocument->createElementNS("http://www.w3.org/2000/svg", "ellipse");
$ellipseElement->setAttribute("cx", "2cm");
$ellipseElement->setAttribute("cy", "4cm");
$ellipseElement->setAttribute("rx", "2cm");
$ellipseElement->setAttribute("ry", "1cm");
$svgElement->appendChild($ellipseElement);


$TransformerFactory = new JavaClass("javax.xml.transform.TransformerFactory");
$transFactory = $TransformerFactory->newInstance();
$myTransformer = $transFactory->newTransformer();
$src = new java("javax.xml.transform.dom.DOMSource", $myDocument);

// print the picture to a memory buffer...
$memoryStream = new java("java.io.ByteArrayOutputStream");
$streamResult = new java("javax.xml.transform.stream.StreamResult", $memoryStream);
$myTransformer->transform($src, $streamResult);
$data = $memoryStream->toByteArray();

// ... and write it to the disc
$fp = fopen("ellipse.svg", "w");
fwrite($fp, $data);
fclose($fp);

?>
