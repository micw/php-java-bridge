<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$system = new java("java.lang.System");
$t1=$system->currentTimeMillis();
java_set_library_path("http://php-java-bridge.sourceforge.net/kawa.jar"); //load scheme interpreter
$s = new java("kawa.standard.Scheme");
for($i=0; $i<100; $i++) {
  $res=$s->eval("

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
