<?php

/**
 * Fetch Java from the back end
 */
$pwd=dirname($_SERVER["PHP_SELF"]);
require_once("http://127.0.0.1:$_SERVER[SERVER_PORT]$pwd/java/Java.inc");

/**
 * This class keeps the state of our simple application.  The
 * framework will save/restore the state if necessary.
 */
class helloWorld {
  /* the state variable(s) */

  var $name="myName";


  /* standard getter and setter for all state variables */

  function getValue($idx) {
    $prop = java_values($idx);
    return $this->$prop;
  }

  function setValue($idx, $val) {
    $prop = java_values($idx);
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

      $message = new Java("javax.faces.application.FacesMessage", $value->__toString()." invalid, enter yourname");
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
