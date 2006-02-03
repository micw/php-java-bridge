<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$system = new java("java.lang.System");
$t1=$system->currentTimeMillis();

// load scheme interpreter
// try to load local kawa.jar, otherwise load it from sf.net
java_require("kawa.jar");
java_require("http://php-java-bridge.sourceforge.net/kawa.jar");

$s = new java("kawa.standard.Scheme");
for($i=0; $i<100; $i++) {
  $res=(float)$s->eval("

(letrec
 ((f (lambda(v)
       (if 
	   (= v 0)
	   1
	 (*
	  (f
	   (- v 1))
	  v)))))
 (f $i))

");

  if($ex=java_last_exception_get()) $res=$ex->toString();
  echo "fact($i) ==> $res\n";
}
$t2=$system->currentTimeMillis();
$delta=($t2-$t1)/1000.0;
$now=new java("java.sql.Timestamp",$system->currentTimeMillis());
echo  "Evaluation took $delta s -- at: ".$now->toString() . "\n";
?>
