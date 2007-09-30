<?php 

  /* 
   * Use the old style which works with PHP 4 and PHP 5. Note that we
   * cannot use java_autoload() and try/catch because PHP 4 doesn't
   * support these features.
   */
if(!extension_loaded("java")) {
  die("Please create and install the java.so or php_java.dll from the source download");
 }

/*
 * check if this is really the PHP/Java Bridge from sourceforge
 */
if(!function_exists("java_get_server_name")) {
  die("Fatal: The loaded java extension is not the PHP/Java Bridge");
 }


phpinfo();

$System = new JavaClass("java.lang.System");
$props = $System->getProperties();
$array = java_values($props);
foreach($array as $k=>$v) {
  echo "$k=>$v"; echo "<br>\n";
}
echo "<br>\n";
  
/* create a PHP class which implements the Java toString() method */
class MyClass {
  function toString() { return "hello PHP from Java!"; }
}
  
/* create a Java object from the PHP object */
$javaObject = java_closure(new MyClass());
echo "PHP says that Java says: "; echo $javaObject->toString();  echo "<br>\n";
echo "<br>\n";

?>
