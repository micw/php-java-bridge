<!-- PHP version of numberguess.jsp -->

<?php 

if(!array_key_exists('guess', $_GET)) {		// start fresh
  ($session=java_session("numberGuess"));
  $session->destroy();
}

$session = java_session("numberGuess");
if($session->isNew()) {
  $session->put("bean", $numguess=new java("num.NumberGuessBean"));
} else {
  $numguess=$session->get("bean");
}
if($_GET['guess']) {
  $numguess->setGuess($_GET['guess']);
}
?>

<html>
<head><title>Number Guess</title></head>
<body bgcolor="white">
<font size=4>

<?php if($numguess->getSuccess()) { ?>
  Congratulations!  You got it.
  And after just <?php echo $numguess->getNumGuesses(); ?> tries.<p>

  <?php $session->destroy(); ?>

  Care to <a href="numberguess.php">try again</a>?

<?php } else if ($numguess->getNumGuesses() == 0) { ?>

  Welcome to the Number Guess game.<p>

  I'm thinking of a number between 1 and 100.<p>

  <form method=get>
  What's your guess? <input type=text name=guess>
  <input type=submit value="Submit">
  </form>

<?php } else { ?>

  Good guess, but nope.  Try <b><?php echo $numguess->getHint() ?></b>.

  You have made <?php echo $numguess->getNumGuesses() ?> guesses.<p>

  I'm thinking of a number between 1 and 100.<p>

  <form method=get>
  What's your guess? <input type=text name=guess>
  <input type=submit value="Submit">
  </form>

<?php } ?>

</font>
</body>
</html>
