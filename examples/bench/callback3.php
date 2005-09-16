#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}
ini_set("max_execution_time", 0);

class HandlerBase {
  function resolveEntity ($publicId, $systemId) 
  {
    return null;
  }
    
  function notationDecl ($name, $publicId, $systemId)
  {
  }
    
  function unparsedEntityDecl ($name, $publicId, $systemId, $notationName)
  {
  }
    
  function setDocumentLocator ($locator)
  {
  }
    
  function startDocument ()
  {
    echo "\n";
  }

  function endDocument ()
  {
    echo "\n";
  }
  function startElement ($name, $attributes) 
  {
    echo "[";
  }

  function endElement ($name)
  {
    echo "]";
  }
    
  function characters ($chars, $start, $length)
  {
    $s = new java("java.lang.String", $chars, $start, $length);
    echo $s->toString();
  }
  function ignorableWhitespace ($chars, $start, $length)
  {
    $s = new java("java.lang.String", $chars, $start, $length);
    echo $s->toString();
  }

  function processingInstruction ($target, $data)
  {
  }
    
    
  function warning ($e)
  {
    echo "callback warning called with args $e<br>\n";
  }
    
  function error ($e)
  {
    echo "callback error called with args $e<br>\n";
  }
    
  function fatalError ($e)
  {
    echo "callback fatalError called with args $e<br>\n";
    //throw $e;
  }
}

// The interfaces that our HandlerBase implements
function getInterfaces() {
  return array(new JavaClass("org.xml.sax.EntityResolver"),
	       new JavaClass("org.xml.sax.DTDHandler"),
	       new JavaClass("org.xml.sax.DocumentHandler"),
	       new JavaClass("org.xml.sax.ErrorHandler"));
}

// Create an instance of HandlerBase which implements the above
// interfaces.
function createHandler() {
  return java_closure(new HandlerBase(), null, getInterfaces());
}

$sys=new java("java.lang.System");
$stderr = fopen('php://stderr', 'w');
$result = array();

$n=500;
for($i=0; $i<$n; $i++) {
$t1=$sys->currentTimeMillis();

// Standard SAX handling
$ParserFactory=new JavaClass("javax.xml.parsers.SAXParserFactory");
$factory=$ParserFactory->newInstance();
$saxParser=$factory->newSaxParser();
$parser=$saxParser->getParser();

$handler=createHandler();
$parser->setDocumentHandler($handler);
$parser->setErrorHandler($handler);

// and filter it through the above callbacks
$here=getcwd();
$inputSource=new java("org.xml.sax.InputSource", "$here/../XML/phpinfo.xml");
$parser->parse($inputSource);
$t2=$sys->currentTimeMillis();
$result[$i]=$t2-$t1;
fwrite ($stderr, "$result[$i]\n");
}
for($sum=0, $i=0; $i<$n; $i++) {
  $sum+=$result[$i];
}
fwrite($stderr, "---\n" . ($sum/$n));

?>
