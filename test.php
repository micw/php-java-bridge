<?php 
if(!extension_loaded("java")) {
  $n = php_sapi_name();
  if($n=="cgi"||$n=="cgi-fcgi"||$n=="cli") @dl("java.so")||@dl('php_java.dll');
 }
if(!extension_loaded("java")) {
  $port= (isset($_SERVER['SERVER_PORT']) && (($_SERVER['SERVER_PORT'])>1024)) ? $_SERVER['SERVER_PORT'] : '8080';
  require_once("http://127.0.0.1:${port}/JavaBridge/java/Java.inc");
 }

  /* 
   * Use the new style which works with PHP 5 and above only. This
   * code makes use of the new PHP 5 features try/catch, the PHP
   * standard library and the automatic __toString() conversion. 
   *
   * See the test.php4 if you still use PHP 4.
   */

/* autoload NAME.jar from include_path/NAME/NAME.jar or ~/lib/NAME.jar */
java_autoload(/*"myLib1.jar;myLib2.jar;..."*/);

phpinfo();

try {

  /* invoke java.lang.System.getProperties() */
  $props = java_lang_System::type()->getProperties();
  
  /* convert the result object into a PHP array */
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
  echo "PHP says that Java says: "; echo $javaObject;  echo "<br>\n";
  echo "<br>\n";
  

  echo php_java_bridge_Util::type()->VERSION; echo "<br>\n";

} catch (JavaException $ex) {
  echo "An exception occured: "; echo $ex; echo "<br>\n";
}

?>
