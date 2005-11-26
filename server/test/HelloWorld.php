<?php
dl('java.so');
function toString() {
  return "PHP:FOO";
}
function hashCode() {
  return java_closure(new foo());
}

class foo {
  function toString() {
    return "miaow";
  }
}

class bar {
function toString() {
return "i am bar";
}
var $i;
function hello($a, $b) {
echo "hello $a . $b";
}
}
function getBar() {
	return java_closure(new bar());
}
echo "running...";
java_context()->call(java_closure()) || die("You must call this script from java!");
?>
