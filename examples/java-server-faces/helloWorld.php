<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

/**
 * This class keeps the state of our simple application.  The
 * framework will save/restore the state if necessary.
 */
class helloWorld {
  /* the state variable(s) */

  var $name="myName";


  /* standard getter and setter for all state variables */

  function getValue($prop) {
    return $this->$prop;
  }

  function setValue($prop, $val) {
    $this->$prop = $val;
  }


  /* User functions */

  /* see h:commandButton #1 in the UI */
  function send() {
    return "success";
  }

  /* see validator of h:inputTest #1 in the UI */
  function xvalidate($ctx, $arg, $value) {
    // this message goes to the server log
    echo "helloWorld.php:: validate: $value";

    if($value->equals("myName")) {
      // this message goes to the server log
      echo "helloWorld.php:: throws validate exception.";

      $message = new Java("javax.faces.application.FacesMessage", "$value invalid, enter yourname");
      throw 
	new JavaException("javax.faces.validator.ValidatorException", $message);
    }
  }
}

/* 
 * check if we're called from the framework, redirect to index.php if
 * not
 */
java_context()->call(java_closure(new helloWorld())) ||include("index.php");

// this message goes to the server log
echo "helloWorld.php:: script terminated";

?>
