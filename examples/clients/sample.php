<?php
class Protocol {
  const Pc="<C v=\"%s\" p=\"I\">", PC="</C>";
  const Pi="<I v=\"%d\" m=\"%s\" p=\"I\">", PI="</I>";
  const Ps="<S v=\"%s\"/>", Pl="<L v=\"%d\" p=\"%s\"/>", Po="<O v=\"%d\"/>";
  var $c;
  
  function __construct() { $this->c=fsockopen("192.168.5.203",9267); fwrite($this->c, "\177@"); }

  function createBegin($s) { fwrite($this->c, sprintf(self::Pc, $s)); }
  function createEnd() { fwrite($this->c, self::PC); }

  function invokeBegin($o, $m) { fwrite($this->c, sprintf(self::Pi, $o, $m)); }
  function invokeEnd() { fwrite($this->c, self::PI); }

  function writeString($s) {fwrite($this->c, sprintf(self::Ps, $s));}
  function writeInt($s) { fwrite($this->c, sprintf(self::Pl, $s<0?-$s:$s, $s<0?"A":"P")); }
  function writeObject($s) { fwrite($this->c, sprintf(self::Po, $s->java)); }
  function writeVal($s) {
    if(is_string($s)) $this->writeString($s);
    else if(is_int($s)) $this->writeInt($s);
    else $this->writeObject($s);
  }

  function getResult() { $res = fread($this->c, 8192); $ar = sscanf($res, '%s v="%[^"]"'); var_dump($ar); return $ar[1]; }
}

function getProtocol() { static $protocol; if(!isset($protocol)) $protocol=new Protocol(); return $protocol; }

class Java {
  var $java;
  function __construct() {
    if(!func_num_args()) return;
    $protocol=getProtocol();
    $ar = func_get_args();
    $protocol->createBegin(array_shift($ar));
    foreach($ar as $arg) { $protocol->writeVal($arg); }
    $protocol->createEnd();
    $ar = sscanf($protocol->getResult(), "%d");
    $this->java=$ar[0];
  }
  function __call($method, $args) {
    $protocol=getProtocol();
    $protocol->invokeBegin($this->java, $method);
    foreach($args as $arg) { $protocol->writeVal($arg); }
    $protocol->invokeEnd();
    $proxy = new Java();
    $ar = sscanf($protocol->getResult(), "%d");
    $proxy->java=$ar[0];
    return $proxy;
  }
  function toString() {
    $protocol=getProtocol();
    $protocol->invokeBegin("", "castToString");
    $protocol->writeVal($this);
    $protocol->invokeEnd();
    return $protocol->getResult();
  }
}
// Test
$i1 = new Java("java.math.BigInteger",  "1");
$i2 = new Java("java.math.BigInteger",  "2");
$i3 = $i1->add($i2);
echo $i3->toString() . "\n";
?>
