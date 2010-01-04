<?php /*-*- mode: php; tab-width:4 -*-*/

  /**
	 PHPDebugger.inc version 1.0alpha -- A pure PHP debugger.

	 Copyright (C) 2009 Jost Boekemeier

	 This file is part of the PHPDebugger.

	 The PHPDebugger ("the library") is free software; you can
	 redistribute it and/or modify it under the terms of the GNU General
	 Public License as published by the Free Software Foundation; either
	 version 2, or (at your option) any later version.

	 The library is distributed in the hope that it will be useful, but
	 WITHOUT ANY WARRANTY; without even the implied warranty of
	 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
	 General Public License for more details.

	 You should have received a copy of the GNU General Public License
	 along with the PHPDebugger; see the file COPYING.  If not, write to the
	 Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
	 02111-1307 USA.

	 Linking this file statically or dynamically with other modules is
	 making a combined work based on this library.  Thus, the terms and
	 conditions of the GNU General Public License cover the whole
	 combination.

	 As a special exception, the copyright holders of this library give you
	 permission to link this library with independent modules to produce an
	 executable, regardless of the license terms of these independent
	 modules, and to copy and distribute the resulting executable under
	 terms of your choice, provided that you also meet, for each linked
	 independent module, the terms and conditions of the license of that
	 module.  An independent module is a module which is not derived from
	 or based on this library.  If you modify this library, you may extend
	 this exception to your version of the library, but you are not
	 obligated to do so.  If you do not wish to do so, delete this
	 exception statement from your version. */

define ("PDB_DEBUG", 0);
ini_set("max_execution_time", 240);


  /**
   * Usage:
   * 
   * 1. include() this file at the beginning of your script
   *
   * 2. browse to your script using firefox 
   *
   * 3. set breakpoints using the JavaScript GUI, click on any
   * variable or included file to visit that variable or file
   * 
   * 4. click on the stop button to terminate the debug session
   *
   */

class pdb_Logger {
  const FATAL = 1;
  const INFO = 2;
  const VERBOSE = 3;
  const DEBUG = 4;

  private static $logLevel = 0;
  private static $logFileName = "/tmp/pdb_PHPDebugger.log";

  private static function println($msg, $level) {
	if (!self::$logLevel) self::$logLevel=PDB_DEBUG?self::DEBUG:self::INFO;
	if ($level <= self::$logLevel) {
	  static $file = null;
	  if (!$file) $file = fopen(self::$logFileName, "ab") or die("fopen");
	  fwrite($file, time().": ");
	  fwrite($file, $msg."\n");
	  fflush($file);
	}
  }

  public static function logFatal($msg) {
	self::println($msg, self::FATAL);
  }
  public static function logInfo($msg) {
	self::println($msg, self::INFO);
  }
  public static function logMessage($msg) {
	self::println($msg, self::VERBOSE);
  }
  public static function logDebug($msg) {
	self::println($msg, self::DEBUG);
  }
  public static function debug($msg) {
	self::logDebug($msg);
  }
  public static function log($msg) {
	self::logMessage($msg);
  }
  public static function setLogLevel($level) {
	self::$logLevel=$level;
  }
  public static function setLogFileName($name) {
	self::$logFileName = $name;
  }
}

/**
 * This class represents the debugger front end connection.  It
 * communicates with the debugger back end using a shared-memory queue.
 */
class pdb_ClientConnection {
  protected $id;
  protected $role, $to;
  const TIMER_DURATION = 200000; // 200ms

  /**
   * Create a new communication using a unique id
   */
  public function pdb_ClientConnection($id) {
    $this->id = $id;
    $this->init();
  }
  protected function init() {
    $this->role = "client";
    $this->to   = "server";
  }
  private function getCookies() {
    $str="";
    $first=true;
    foreach($_COOKIE as $k => $v) {
      $str .= ($first ? "Cookie: $k=$v":"; $k=$v");
      $first=false;
    }
    if(!$first) $str .= "\r\n";
    return $str;
  }

  protected function poll() {
    $val = "";
    $chanCtr = "pdb_ctr{$this->role}{$this->id}";
    $chan = "pdb_{$this->role}{$this->id}";
    session_start();
    if (!isset($_SESSION[$chanCtr])) $_SESSION[$chanCtr] = 0; 
    if (isset($_SESSION[$chan]) && (count($_SESSION[$chan]) > $_SESSION[$chanCtr])) {
      $val = $_SESSION[$chan][$_SESSION[$chanCtr]++];
    }
    session_write_close();
    return $val;
  }
  protected function send($val) {
    $chan = "pdb_{$this->to}{$this->id}";
    session_start();
    $_SESSION[$chan][]=$val;
    session_write_close();
  }

  /**
   * read a new value from the read queue
   */
  public function read() {
    $val = null;
    while(!($val=$this->poll())) {
      usleep(self::TIMER_DURATION);
    }
    return $val;
  }
  /**
   * write a new value to the write queue
   */
  public function write($val) {
    $this->send($val);
  }
  /**
   * shut down the communication channel
   */
  public function shutdown() {
	pdb_Logger::debug("shutdown comm channel");
	session_destroy();
  }
}

/**
 * The PHP parser
 */
class pdb_Parser {
  const BLOCK = 1;
  const STATEMENT = 2;
  const EXPRESSION = 3;

  private $scriptName, $content;
  private $code;
  private $output;
  private $line, $currentLine;
  private $beginStatement, $inPhp, $inDQuote;
  
  /**
   * Create a new PHP parser
   * @param string the script name
   * @param string the script content
   */
  public function pdb_Parser($scriptName, $content) {
    $this->scriptName = $scriptName;
    $this->content = $content;
    $this->code = token_get_all($content);
    $this->output = "";
    $this->line = $this->currentLine = 0;
    $this->beginStatement = $this->inPhp = $this->inDQuote = false;
  }

  private function toggleDQuote($chr) {
	if ($chr == '"') $this->inDQuote = !$this->inDQuote;
  }

  private function each() {
	$next = each ($this->code);
	if ($next) {
	  $cur = current($this->code);
	  if (is_array($cur)) {
		$this->currentLine = $cur[2];
		if ($this->isWhitespace($cur)) {
		  $this->write($cur[1]);
		  return $this->each();
		}
	  }
	  else 
		$this->toggleDQuote($cur);
	}
	return $next;
  }

  private function write($code) {
    //echo "write:::".$code."\n";
    $this->output.=$code;
  }

  private function writeInclude() {
    $name = "";
    while(1) {
	  if (!$this->each()) die("parse error");
      $val = current($this->code);
      if (is_array($val)) {
		$name.=$val[1];
      } else {
		if ($val==';') break;
		$name.=$val;
      }
    }
    if (PDB_DEBUG == 2) 
      $this->write("EVAL($name);");
    else
      $this->write("eval('?>'.pdb_startInclude($name)); pdb_endInclude();");
  }

  private function writeCall() {
    while(1) {
	  if (!$this->each()) die("parse error");
      $val = current($this->code);
      if (is_array($val)) {
		$this->write($val[1]);
      } else {
		$this->write($val);
		if ($val=='{') break;
      }
    }
	$scriptName = $this->scriptName;
    $this->write("\$__pdb_CurrentFrame=pdb_startCall(\"$scriptName\");");
  }

  private function writeStep($pLevel) {
    $token = current($this->code);
    if ($this->inPhp && !$pLevel && !$this->inDQuote && $this->beginStatement && !$this->isWhitespace($token) && ($this->line != $this->currentLine)) {
      $lastLine = $this->line = $this->currentLine;
      $scriptName = $this->scriptName;
      if (PDB_DEBUG == 2)
		$this->write(";STEP($lastLine);");
      else
		$this->write(";pdb_step(\"$scriptName\", $lastLine, pdb_getDefinedVars(get_defined_vars(), (isset(\$this) ? \$this : NULL)));");
    }
  }

  private function writeNext() {
	$this->next();
	$token = current($this->code);
	if (is_array($token)) $token = $token[1];
	$this->write($token);
  }

  private function nextIs($chr) {
    $i = 0;
    while(each($this->code)) {
      $cur = current($this->code);
      $i++;
      if (is_array($cur)) {
		switch ($cur[0]) {
		case T_COMMENT:
		case T_DOC_COMMENT:
		case T_WHITESPACE:
		  break;		/* skip */
		default: 
		  while($i--) prev($this->code);
		  return false;		/* not found */
		}
      } else {
		while($i--) prev($this->code);
		return $cur == $chr;	/* found */
      }
    }
    while($i--) prev($this->code);
    return false;		/* not found */
  }

  private function nextTokenIs($ar) {
    $i = 0;
    while(each($this->code)) {
      $cur = current($this->code);
      $i++;
      if (is_array($cur)) {
		switch ($cur[0]) {
		case T_COMMENT:
		case T_DOC_COMMENT:
		case T_WHITESPACE:
		  break;		/* skip */
		default: 
		  while($i--) prev($this->code);
		  return (in_array($cur[0], $ar));
		}
      } else {
		break; /* not found */
      }
    }
    while($i--) prev($this->code);
    return false;		/* not found */
  }

  private function isWhitespace($token) {
    $isWhitespace = false;
    switch($token[0]) {
	case T_COMMENT:
	case T_DOC_COMMENT:
	case T_WHITESPACE:
	  $isWhitespace = true;
	  break;
    }
    return $isWhitespace;
  }
  private function next() {
	if (!$this->each()) trigger_error("parse error", E_USER_ERROR);
  }

  private function parseBlock () {
	$this->parse(self::BLOCK);
  }
  private function parseStatement () {
	$this->parse(self::STATEMENT);
  }
  private function parseExpression () {
	$this->parse(self::EXPRESSION);
  }

  private function parse ($type) {
	pdb_Logger::debug("parse:::$type");

    $this->beginStatement = true;
	$pLevel = 0;

    do {
      $token = current($this->code);
      if (!is_array($token)) {
		pdb_Logger::debug(":::".$token);
		$this->write($token);
		if ($this->inPhp && !$this->inDQuote) {
		  $this->beginStatement = false; 
		  switch($token) {
		  case '(': 
			$pLevel++;
			break;
		  case ')':
			if (!--$pLevel && $type==self::EXPRESSION) return;
			break;
		  case '{':  
			$this->next();
			$this->parseBlock(); 
			break;
		  case '}': 
			if (!$pLevel) return;
			break;
		  case ';':
			if (!$pLevel) {
			  if ($type==self::STATEMENT) return;
			  $this->beginStatement = true; 
			}
			break;
		  }
		}
      } else {
		pdb_Logger::debug(":::".$token[1].":(".token_name($token[0]).')');

		if ($this->inDQuote) {
		  $this->write($token[1]);
		  continue;
		}

		switch($token[0]) {

		case T_OPEN_TAG: 
		case T_START_HEREDOC:
		case T_OPEN_TAG_WITH_ECHO: 
		  $this->beginStatement = $this->inPhp = true;
		  $this->write($token[1]);
		  break;

		case T_END_HEREDOC:
		case T_CLOSE_TAG: 
		  $this->writeStep($pLevel);

		  $this->write($token[1]);
		  $this->beginStatement = $this->inPhp = false; 
		  break;

		case T_FUNCTION:
		  $this->write($token[1]);
		  $this->writeCall();
		  $this->beginStatement = true;
		  break;

		case T_ELSE:
		  $this->write($token[1]);
		  if ($this->nextIs('{')) {
			$this->writeNext();
			$this->next();

			$this->parseBlock();
		  } else {
			$this->next();

			/* create an artificial block */
			$this->write('{');
			$this->beginStatement = true;
			$this->writeStep($pLevel);
			$this->parseStatement();
			$this->write('}');

		  }
		  if ($type==self::STATEMENT) return;

		  $this->beginStatement = true;
		  break;

		case T_DO:
		  $this->writeStep($pLevel);
		  $this->write($token[1]);
		  if ($this->nextIs('{')) {
			$this->writeNext();
			$this->next();

			$this->parseBlock();
			$this->next();

		  } else {
			$this->next();

			/* create an artificial block */
			$this->write('{');
			$this->beginStatement = true;
			$this->writeStep($pLevel);
			$this->parseStatement();
			$this->next();
			$this->write('}');
		  }
		  $token = current($this->code);
		  $this->write($token[1]);

		  if ($token[0]!=T_WHILE) trigger_error("parse error", E_USER_ERROR);
		  $this->next();
		  $this->parseExpression();

		  if ($type==self::STATEMENT) return;

		  $this->beginStatement = true;
		  break;

		case T_IF:
        case T_ELSEIF:
        case T_FOR:
		case T_WHILE:
		  $this->writeStep($pLevel);

		  $this->write($token[1]);
		  $this->next();

		  $this->parseExpression();

		  if ($this->nextIs('{')) {
			$this->writeNext();
			$this->next();

			$this->parseBlock();


		  } else {
			$this->next();

			/* create an artificial block */
			$this->write('{');
			$this->beginStatement = true;
			$this->writeStep($pLevel);
			$this->parseStatement();
			$this->write('}');
		  }

		  if ($this->nextTokenIs(array(T_ELSE, T_ELSEIF))) {
			$this->beginStatement = false;
		  } else {
			if ($type==self::STATEMENT) return;
			$this->beginStatement = true;
		  }
		  break;

		case T_INCLUDE: 
		case T_INCLUDE_ONCE: 
		case T_REQUIRE: 
		case T_REQUIRE_ONCE: // FIXME: implement require and _once
		  $this->writeStep($pLevel);
		  $this->writeInclude();

		  if ($type==self::STATEMENT) return;

		  $this->beginStatement = true;
		  break;

		case T_CLASS:
		case T_CASE:
		case T_DEFAULT:
		case T_PUBLIC:
		case T_PRIVATE:
		case T_PROTECTED:
		case T_STATIC:
		case T_CONST:
		case T_GLOBAL:
		case T_ABSTRACT:
		  $this->write($token[1]);
		  $this->beginStatement = false;
		  break;

		default:
		  $this->writeStep($pLevel);
		  $this->write($token[1]);
		  $this->beginStatement = false;
		  break;
	
		}
      }
    } while($this->each());
  }

  /**
   * parse the given PHP script
   * @return the parsed PHP script
   */
  public function parseScript() {
    do {
      $this->parseBlock();
    } while($this->each());

    return $this->output;
  }
}

/**
 * This structure represents the debugger front-end.  It is used by the
 * JavaScript code to communicate with the debugger back end.
 */
class pdb_JSDebuggerClient {
  private static function getDebuggerFilename() {
	$script = __FILE__;
	$scriptName = basename($script);
	return $scriptName == "PHPDebugger.php" ? $script :"java/PHPDebugger.php";
  }
  private static function getCurrentRootDir() {
	$scriptName = $_SERVER['SCRIPT_NAME'];
	$scriptFilename = $_SERVER['SCRIPT_FILENAME'];
  
  
	$scriptDirName = dirname($scriptName);
	$scriptDir     = dirname($scriptFilename);
  
	if ((strlen($scriptDirName)>1) && ($scriptDirName[1]=='~')) {
	  $scriptDirName = ltrim($scriptDirName, "/");
	  $idx = strpos($scriptDirName, '/');
	  $scriptDirName = $idx===false ? '' : substr($scriptDirName, $idx);
	} elseif ((strlen($scriptDirName)==1) && ($scriptDirName[0]=='/')) {
	  $scriptDirName = '';
	}
	return substr($scriptDir, 0, strlen($scriptDir)-strlen($scriptDirName));
  }
  public static function getDebuggerURL() {
	$path = realpath(self::getDebuggerFilename());
	if (!$path) 
	  trigger_error("No such file or directory: $path", E_USER_ERROR);

	$root = self::getCurrentRootDir();

	$scriptName = $_SERVER['SCRIPT_NAME'];

	$scriptDirName = dirname($scriptName);
	$prefix = '';
	if ((strlen($scriptDirName)>1) && ($scriptDirName[1]=='~')) {
	  $scriptDirName = ltrim($scriptDirName, "/");
	  $idx = strpos($scriptDirName, '/');
	  $prefix = '/' . ($idx ? substr($scriptDirName, 0, $idx): $scriptDirName);
	}
    
	$path = "${prefix}" . substr($path, strlen($root));
	$pathInfo = isset($_SERVER['PATH_INFO']) ? $_SERVER['PATH_INFO'] : "";
	$query = isset($_SERVER['QUERY_STRING']) ? $_SERVER['QUERY_STRING'] : "";

	$url = "${path}${pathInfo}";
	if ($query) $url .= "?${query}";
	return $url;
  }

  public static function getPostData() {
    $str = '';
    foreach ($_POST as $key => $value) {
        if ($str) $str .= '&';
        $str .= $key . '=' . urlencode($value);
    }
	return $str;
  }

  public static function getServerID() {
	if (!isset($_SESSION['pdb_ServerID'])) 
	  return $_SESSION['pdb_ServerID'] = 1;
	else 
	  return ++$_SESSION['pdb_ServerID'];
  }

  public static function handleRequest($vars) {
	$msg = (object)$vars;
	
	if ($msg->cmd == "begin") sleep(1); // FIXME wait for the server to settle
	pdb_Logger::debug("beginHandleRequest: ".$msg->cmd);
	$conn = new pdb_ClientConnection($msg->serverID);
	$conn->write(json_encode($msg));
	$response = $conn->read();
	
	if ($msg->cmd == "end") $conn->shutdown();

	$output = $response;
	echo "($output)";
	pdb_Logger::debug("endHandleRequest");
  }
}

/**
 * The current view frame. Contains the current script name. May be
 * selected by clicking on a include() hyperlink Allows users to set
 * breakpoints for this file.
 *
 * View will be discarded when go, step, end is invoked.
 */
class pdb_View {
  /** The current script name */
  public $scriptName;
  /** Back-link to the parent or null */
  public $parentView;

  protected $bpCounter, $lineCounter, $code;

  /**
   * Create a new view frame
   * @param object the parent frame
   * @param string the script name
   */
  public function pdb_View($parentView, $scriptName) {
    $this->parentView = $parentView;
    $this->scriptName = $scriptName;

    $this->bpCounter = $this->lineCounter = 1;
    $this->code = null;
  }
  private function breakpointCounter ($val) {
    if ($val[0]=="line#") 
      return (string)$this->lineCounter++;
    else
      return 'id="bp_'.$this->bpCounter++.'"';
  }
  /**
   * Return the HTML representation of the current script
   * @return string the clickable HTML representation of the current script
   */
  public function getHtmlScriptSource() {
    if (!$this->code) {
      $c=
		'<span class="breakpoint" id="bp_" onmousedown="return toggleBreakpoint(this, event);">'.
		'<span class="currentlineIndicator normal"></span>'.
		'<span class="linenumber">line#</span>'.
		'<span class="breakpointIndicator normal"></span>'.
		'</span><br />';
      $this->code = 
		preg_replace_callback(array('|id="bp_"|', // id=bp_ => id=bp_$n
									'|line#|'),  // lineno => $n
							  array($this, "breakpointCounter"),
							  preg_replace(array('|<br />|', // br => span id=pb_ ...
												 '|<span style="color: #0000BB">\?&gt;</span>|'), // handle incomplete last line
										   array($c, 
												 '<span style="color: #0000BB">?&gt;'.$c.'</span>'),
										   show_source($this->scriptName, true)));
    }
    return $this->code;
  }
}
/**
 * The current view. Used to show the contents of a variable
 */
class pdb_VariableView extends pdb_View {
  /**
   * Create a new variable view
   * @param object the parent frame
   * @param string the variable name
   * @param string the variable value
   */
  public function pdb_VariableView($parentView, $name, $value) {
    parent::pdb_View($parentView, $name);
    $this->value = $value;
  }
  /**
   * {@inheritDoc}
   */
  public function getHtmlScriptSource() {
    return $this->value;
  }
}
/**
 * The current execution frame. Contains the current run-time script
 * name along with its state
 */
class pdb_Environment extends pdb_View {
  /** bool true if a dynamic breakpoint should be inserted at the next line, false otherwise */
  public $stepNext;
  /** The execution vars */
  public $vars;
  /** The current line */
  public $line;

  /**
   * Create a new execution frame
   * @param string the script name
   * @param bool true if a dynamic breakpoint should be inserted at the next line, false otherwise
   */
  public function pdb_Environment($scriptName, $stepNext) {
    parent::pdb_View(null, $scriptName);
    $this->stepNext = $stepNext;
    $this->line = -1;

  }
  /**
   * Update the execution frame with the current state
   * @param string the current script name
   * @param int the current execution line
   * @param mixed the current variables
   */
  public function update ($line, &$vars) {
    $this->line = $line;
    $this->vars = &$vars;
  }
}
/**
 * Represents a breakpoint
 */
class pdb_Breakpoint {
  /** The script name */
  public $scriptName;
  /** The current line */
  public $line;
  /** The breakpointName as seen by JavaScript */
  public $breakpoint;
  /* The breakpoint type (not used yet) */
  public $type;

  /**
   * Create a new breakpoint
   * @param string the breakpoint name
   * @param string the script name
   * @param int the line
   */
  public function pdb_Breakpoint($breakpointName, $scriptName, $line) {
    $this->breakpoint = $breakpointName;
    $this->scriptName = $scriptName;
    $this->line = $line;

    $this->type = 1;
  }
  /**
   * @return the string representation of the breakpoint
   */
  public function __toString() {
	return "{$this->line}@{$this->scriptName}, js name: ({$this->breakpoint}, type: {$this->type})";
  }
}
/**
 * The current debug session. Contains the current environment stack,
 * script output and all breakpoints set by the client.  An optional
 * view is set by the switchView command.
 */
final class pdb_Session {
  /** The collection of breakpoints */
  public $breakpoints;

  /** The environment tree */
  public $environments;
  /** The current execution frame */
  public $currentFrame;
  /** The current view */
  public $currentView;
  /** The script output */
  public $output;

  /**
   * Create a new debug session for a given script
   * @param string the script name
   */
  public function pdb_Session($scriptName) {
    $this->breakpoints = $this->lines = array();
    $this->environments = array($this->currentFrame = new pdb_Environment($scriptName, true));

    $this->currentView = null;
    $this->end = false;
  }
  /**
   * Return the clickable HTML script source, either from the cusom view or from the current frame
   * @return string the HTML script source
   */
  public function getCurrentViewHtmlScriptSource () {
    return $this->currentView ? $this->currentView->getHtmlScriptSource() : $this->currentFrame->getHtmlScriptSource();
  }      
  /**
   * Return the current frame script name
   * @return string the script name of the current frame
   */
  public function getScriptName () {
    return $this->currentFrame->scriptName;
  }
  /**
   * Return the current script name, either from the view or from the current frame
   * @return string the current script name
   */
  public function getCurrentViewScriptName () {
    return $this->currentView ? $this->currentView->scriptName : $this->getScriptName();
  }      
  /**
   * Return the breakpoints for the current script
   * @return object the breakpoints
   */
  public function getBreakpoints () {
    $bp = array();
    foreach ($this->breakpoints as $breakpoint) {
      if ($this->getCurrentViewScriptName() != $breakpoint->scriptName) continue;
      array_push($bp, $breakpoint->breakpoint);
    }
    return $bp;
  }
  /**
   * toggle and write breakpoint reply
   * @param object the current comm. channel
   * @param object the breakpoint
   */
  public function writeToggleBreakpoint($channel, $breakpoint) {
    $id = $breakpoint."@".$this->getCurrentViewScriptName();
    if (!isset($this->breakpoints[$id])) {
      $this->breakpoints[$id] = new pdb_Breakpoint($breakpoint, $this->getCurrentViewScriptName(), substr($breakpoint, 3));
      $channel->write(array("cmd"=>"setBreakpoint", "scriptName"=>$this->getCurrentViewScriptName(), "breakpoint"=>$breakpoint));
    } else {
      $bp = $this->breakpoints[$id];
      unset ($this->breakpoints[$id]);
      $channel->write(array("cmd"=>"unsetBreakpoint", "scriptName"=>$bp->scriptName, "breakpoint"=>$bp->breakpoint));
    }
  }
  /**
   * check if there's a breakpoint
   * @param string the script name
   * @param int the line within the script 
   * @return true if a breakpoint exists at line, false otherwise
   */
  public function hasBreakpoint($scriptName, $line) {
    static $first = true;
    
    if ($this->currentFrame->stepNext) return true;
    
    foreach ($this->breakpoints as $breakpoint) {
	  pdb_Logger::debug("process breakpoint::: $scriptName, $line::  $breakpoint");
      if($breakpoint->type==1) {
		if ($first && $breakpoint->scriptName==$scriptName&&$breakpoint->line==-1) {
		  $first = false; return true;
		}
		if ($breakpoint->scriptName==$scriptName&&$breakpoint->line==$line) return true;
      }
    }
    return false;
  }
  /**
   * parse code
   * @param string the script name
   * @param string the content
   * @return the parsed script
   */
  public function parseCode($scriptName, $content) {
	$parser = new pdb_Parser($scriptName, $content);
	return $parser->parseScript();
  }
  private static function doEval($__pdb_Code) {
    return  eval ("?>".$__pdb_Code);
  }
  /**
   * parse and execute script
   * @return the script output
   */
  public function parseScript() {
    $code = $this->parseCode($this->getScriptName(), file_get_contents($this->getScriptName()));

    if (PDB_DEBUG) pdb_Logger::debug("eval:::$code,".$this->getScriptName()."\n");
    if (!PDB_DEBUG) ob_start();
    self::doEval ($code);
    $this->output = ob_get_contents();
    if(!PDB_DEBUG) ob_end_clean();

    return $this->output;
  }
}
/**
 * This class represents the debugger back end connection.  It
 * communicates with the debugger front end using a shared-memory queue.
 */
class pdb_ServerConnection extends pdb_ClientConnection {
  protected function init() {
    $this->to = "client";
    $this->role   = "server";
  }

  protected function poll() {
    $val = "";
    $chanCtr = "pdb_ctr{$this->role}{$this->id}";
    $chan = "pdb_{$this->role}{$this->id}";
    session_start();
    if(!isset($_SESSION[$chanCtr])) { $_SESSION[$chan] = array(); $_SESSION[$chanCtr]=0; }
    if (count($_SESSION[$chan]) > $_SESSION[$chanCtr]) {
      $val = $_SESSION[$chan][$_SESSION[$chanCtr]++];
    }
    session_write_close();
    return $val;
  }

}    
/**
 * The java script debugger server daemon. Contains a debug session
 * and handles debug requests from the client.
 */
class pdb_JSDebugger {
  /** The pdb_Session */
  public $session;
  private $id;
  
  private $end;
  private $conn;
  private $ignoreInterrupt;

  const STEP_INTO = 1;
  const STEP_OVER = 2;
  const GO        = 4;



  /**
   * Create new PHP debugger using a given comm. ID
   * @param int the communication address
   */
  public function pdb_JSDebugger($id) {

    $this->id = $id;
    $this->conn = new pdb_ServerConnection($id);

    $this->end = false;

	$this->ignoreInterrupt = false;
  }
  /**
   * Return the current comm. ID
   * @return int the communication address
   */
  public function getServerID() {
    return $this->id;
  }
  /**
   * Read data from the front end
   * @return object the data 
   */
  public function read() {
    return json_decode($this->conn->read());
  }
  /**
   * Write data to the front end
   * @param object the data
   */
  public function write($data) {
    $data["serverID"] = $this->getServerID();
    if (PDB_DEBUG) pdb_Logger::debug("->".print_r($data, true));
    return $this->conn->write(json_encode($data));
  }
  private function ack() {
    $this->write(array("cmd"=>$this->packet->cmd));
  }
  /**
   * Handle requests from the front end
   */
  public function handleRequests() {
	$this->ignoreInterrupt = false;

    while(true) {
      if (PDB_DEBUG) pdb_Logger::debug("handleRequests: accept");
      
      $this->packet = $this->read();

      if (PDB_DEBUG) pdb_Logger::debug("handleRequests: done accept ".$this->packet->cmd);

      switch($this->packet->cmd) {
      case "status":
		$this->write(array("cmd"=>$this->packet->cmd, 
						   "line"=>$this->session->currentFrame->line, 
						   "scriptName"=>$this->session->getCurrentViewScriptName(), 
						   "breakpoints"=>$this->session->getBreakpoints()));
		break;
	  case "extendedStatus":
		$this->write(array("cmd"=>$this->packet->cmd, 
						   "line"=>$this->session->currentFrame->line, 
						   "scriptName"=>$this->session->getCurrentViewScriptName(), 
						   "script"=>$this->session->getCurrentViewHtmlScriptSource(),
						   "breakpoints"=>$this->session->getBreakpoints()));
		break;
      case "begin":
		chdir (urldecode($this->packet->cwd));
		$this->session = new pdb_Session(urldecode($this->packet->scriptName));
		$this->write(array("cmd"=>$this->packet->cmd, 
						   "scriptName"=>$this->packet->scriptName, 
						   "script"=>$this->session->getCurrentViewHtmlScriptSource()));

		$this->session->parseScript();
		$this->end = true;
		break;
      case "stepNext":
		if ($this->end) break;
		$this->session->currentView = null;
		$this->ack();
		return self::STEP_INTO;
      case "stepOver":
		if ($this->end) break;
		$this->session->currentView = null;
		$this->ack();
		return self::STEP_OVER;
      case "stepOut":
      case "go":
		if ($this->end) break;
		$this->session->currentView = null;
		$this->ack();
		return self::GO;
      case "toggleBreakpoint":
		$this->session->writeToggleBreakpoint($this, $this->packet->breakpoint);
		break;
      case "switchView":
		$name = urldecode($this->packet->scriptName);
		if ($name[0]=='$') {
		  $value = $this->session->currentFrame->vars[substr($name, 1)];
		  $this->session->currentView = new pdb_VariableView($this->session->currentView, $name, $value);
		} else {
		  $value = eval("return ".$name.";");
		  if (file_exists($value)) 
			$this->session->currentView = new pdb_View($this->session->currentView, realpath($value));
		  else
			$this->session->currentView = new pdb_VariableView($this->session->currentView, $name, $value);
		}
		$this->ack();
		break;
      case "backView":
		if ($this->session->currentView)
		  $this->session->currentView = $this->session->currentView->parentView;
		$this->ack();
		break;
      case "output":
		if ($this->session) {
		  if (!$this->end)
			$this->session->output = ob_get_contents();
		  $this->write(array("cmd"=>$this->packet->cmd, 
							 "output"=>$this->session->output));
		} else {
		  $this->ack();
		}
		break;
      case "end":
		$this->session->currentView = null;
		$this->ack();
		$this->shutdown();
		exit(0);
      }
    }
  }
  /**
   * shut down the current comm. channel
   */
  public function shutdown() {
    $this->conn->shutdown();
  }

  /**
   * called at run-time for each frame
   * @return the current frame
   */
  public function startCall($scriptName) {
    /* check for stepOver and stepOut */
    $stepNext = $this->session->currentFrame->stepNext == pdb_JSDebugger::STEP_INTO ? pdb_JSDebugger::STEP_INTO : false;
	
	pdb_Logger::debug("startCall::$scriptName, $stepNext");
    return new pdb_Environment($scriptName, $stepNext);
  }

  protected function resolveIncludePath($scriptName) {
    if (file_exists($scriptName)) return realpath($scriptName);
    $paths = explode(PATH_SEPARATOR, get_include_path());
    foreach ($paths as $path) {
      $scriptName="$path/$scriptName";
      if (file_exists($scriptName)) return realpath($scriptName);
    }
    trigger_error("file $scriptName not found", E_USER_ERROR);
  }
  /**
   * called at run-time for each included file
   * @param string the script name
   * @return string the code
   */
  public function startInclude($scriptName) {
	$isDebugger = (basename($scriptName) == "PHPDebugger.php");
	if (!$isDebugger)
	  $scriptName = $this->resolveIncludePath($scriptName);

	pdb_Logger::debug("scriptName::$scriptName, $isDebugger");

    $stepNext = $this->session->currentFrame->stepNext == pdb_JSDebugger::STEP_INTO ? pdb_JSDebugger::STEP_INTO : false;
    $this->session->currentFrame = new pdb_Environment($scriptName, $stepNext);

    if ($isDebugger) // do not debug self
      $code = "<?php ?>";
    else
      $code = $this->session->parseCode(realpath($scriptName), file_get_contents($scriptName));

    array_push($this->session->environments, $this->session->currentFrame);

	pdb_Logger::debug("include:::".$code);
    return $code; // eval -> pdb_step/MSG_READY or pdb_endInclude/MSG_READY OR FINISH
  }

  /**
   * called at run-time after the script has been included
   */
  public function endInclude() {
    array_pop($this->session->environments);

    $this->session->currentFrame = end($this->session->environments);
  }

  /**
   * called at run-time for each line
   * @param string the script name
   * @param int the current line
   * @param mixed the execution variables
   */
  public function step($scriptName, $line, $vars) {
	if ($this->ignoreInterrupt) return; // avoid spurious step calls from __destruct() method
	$this->ignoreInterrupt = true;

	pdb_Logger::logDebug("step: $scriptName @ $line");
    // pull the current frame from the stack or the top-level environment
    $this->session->currentFrame = (isset($vars['__pdb_CurrentFrame'])) ? $vars['__pdb_CurrentFrame'] : end($this->session->environments);
    unset($vars['__pdb_CurrentFrame']);

    $this->session->currentFrame->update($line, $vars);

    if ($this->session->hasBreakpoint($scriptName, $line)) {
      $stepNext = $this->handleRequests();
	  pdb_Logger::logDebug("continue");
      $this->session->currentFrame->stepNext = $stepNext != pdb_JSDebugger::GO ? $stepNext : false;
    }

	$this->ignoreInterrupt = false;
	pdb_Logger::logDebug("endStep: $scriptName @ $line");
  }
}

/**
 * Convenience function called by the executor
 */
function pdb_getDefinedVars($vars1, $vars2) {
  if(isset($vars2)) $vars1['pbd_This'] = $vars2;

  unset($vars1['__pdb_Code']);	     // see pdb_Message::doEval()

  return $vars1;   
}

/**
 * Convenience function called by the executor
 */
function pdb_startCall($scriptName) {
  global $dbg;
  return $dbg->startCall($scriptName);
}

/**
 * Convenience function called by the executor
 */
function pdb_startInclude($scriptName) {
  global $dbg;
  return $dbg->startInclude($scriptName);
}

/**
 * Convenience function called by the executor
 */
function pdb_endInclude() {
  global $dbg;
  $dbg->endInclude();
}

/**
 * Convenience function called by the executor
 */
function pdb_step($scriptName, $line, $vars) {
  global $dbg;
  $dbg->step($scriptName, $line, $vars);
}

if (PDB_DEBUG==2) {
  $parser = new pdb_Parser("test.php", file_get_contents("test.php"));
  echo $parser->parseScript();
  exit (2);
}

/* * The JavaScript part, invoked after the debugger has been included() * */
if (!isset($_SERVER['HTTP_XPDB_DEBUGGER'])) {
    session_start();
    header("Expires: Sat, 1 Jan 2005 00:00:00 GMT");
    header("Last-Modified: ".gmdate( "D, d M Y H:i:s")." GMT");
    header("Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
    header("Pragma: no-cache");
    header("Content-Type: text/html");
?>
<html>
<head>
<title>
PHPDebugger version 1.0 
</title>
<style type="text/css">
#navigationBar {
    display: block;
    position: relative;
    background: #efefef;
    width: 100%;
    height: auto;
    overflow: hidden;
    margin-bottom: 2px;
}
#run {
    height: 13px;
    width: 17px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
   background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAANCAYAAABPeYUaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwcXGXdF1DwAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABEUlEQVQoz62SoWvDUBDGfx0Rry6VzzUyg4lEVgYmRulfMVUKVWOqTFVWDULVbNxsTSETg8jOzm+QwcSL28ECmwhJGpqwwnrmfXfv7nvf3b2e+TQ//NMsgPRlQfoeQ14E9TBAe0uC9QyAeBoeQfK6IZjuqmC89tHeEoDFxP+TzKqQJK0JksPNlQ/QSWYBiJQVKShd+4DJa+f68hyA0f0M7fR5nKyaSiQTlHwgatB4JXkzB+q8C12RJfNwr522Vr4hy7PjtgOgbAWZKs6SRASt+gfZ8bPBcQck83B/Jl/Fre3S8AGlarx5Kuazu33o2I4atUq1LYi27cUNEj0cE925VdBxxxWOttJZXFrvFN/+jBPYL4uJYFb2zCiIAAAAAElFTkSuQmCC') no-repeat;
}
#terminate {
    height: 13px;
    width: 13px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA0AAAANCAYAAABy6+R8AAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYrNECrm4EAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAwUlEQVQoz52SLQ7CQBCFvyaI4nYlciWSI3CEFkVQGAyOcBU0ClCARCKRHGFlN0HsuK4D0RRasWnKJOPel/fmJ/Ev/6ZnDQD89YzcL51iNc3R2ayC5HZkslx3Qs/97gcBEEBOh7jLfNGOh5RY8egQd7HiQcoG9HULPRZRwthagvdR4chapBUPQELVsZKAL8HUkBZApVVHN5FWuhoKzlEYg06HUaYwhuBcO55RGjbxWynANWdSWc5jte3+iCwHIPnn9z7J30SR7ayFNQAAAABJRU5ErkJggg==') no-repeat;
}
#stepInto {
    height: 13px;
    width: 17px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAANCAYAAABPeYUaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYtD6f61SMAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAA6ElEQVQoz2N8//r9fwYKAQuMcWy6JlYFmq4lDIIqyYQNOTZdk8Gr8jpWBdvaNRkUPz9nkDSswWkII8w72Fyi6JjMoGmRw7Ct3ZDBKvM6Ye9gU3RsuiaDpkkywTBhIqjizwfiAxbdBQhDfqCIYXMxVkN+fGdgCCqaCzXkPYNXJoS9ri+ZeO84FV1nWNYUDfEKFC9rimZwKrpOWph41d1nWNaWzcDw5zvDsrZsBq+6+/ijOHn+MQbBzxBF73kVGeYmWiHSSZMiigHY1LIwMDAw/Di/lOHaC4giQYljDAxIhqC7AJtaRmrkHQBYX2Q4HZvQSwAAAABJRU5ErkJggg==') no-repeat;
}
#stepOver {
    height: 13px;
    width: 17px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAANCAYAAABPeYUaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYsOAZcQW0AAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABSklEQVQoz62TP0jDQBTGv5QMdSgYcEjGgEvHdsyYsW6Kg27SKeJki1PpEAShqJPYKTilHUS3dOyYbu1mhpZmERqIcIEOyRA4h9qaeCcK+uA43uPj9/7cO4GEhOKPJmYdMrMQTJ5A3nxGKO2okCsHkHbr30PIzII/slA9vMO2XGWEUTDG+PEMyXIBpdJiIWuAbjiIXkcYXJUZiHZ0A91wMOzuoVhS8hWRkFDHlCmN55RMbeqYMiUhYY5jypRMbUrjOaMpbGhpBLffgGZ43OFphge33wDSiD+TJAaQJqubY243016a5GKa4UFYP/HAVFFr+1zI8LaM/dNLoKh8BpMFnu9b0M8zkJ9sYKo4vrgGxC0gjdHrNDdJC79dqFrbR6/TZAAA+JXUH1xIy5WIlFRYJ1quoq9ti7ysycTGS/CxqbILZCC8uQn/8XfeAUbdtbmYIYl8AAAAAElFTkSuQmCC') no-repeat;
}
#stepOut {
    height: 13px;
    width: 17px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAANCAYAAABPeYUaAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYsF62NfDQAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABAUlEQVQoz62Tv2vCQBTHP5GsDoJDV8f8C44ZM+Y/EOnSgoOCQ4YOxVE6BH9MoZMdM3TI6CLcJNzmLULGCg4p6HBgoB0SFH9gKvYtj7v3fR/ufe/OSNbJD3dG6RaxGFuIsXUfhB04j0OEfwwyi/q+ZI94NjlsVG2cpyGRb1FvKQCMa54kywAZ9XE9eV78FoSjZ+yWun4S+dnH9aao+QAVBQC43hS2ktDvYLfVXz3RqCjYN5wCiiEpkOos5+vw7RhQbGyqIU2yDNhddfs70TlIFwxsAjTfBZVNnN1IuUbQqJ+i9nFJawJoOWGxykSVBwE5xPFiPl5rOC/xAXlBa/zH3/kFlslu4rTXaTUAAAAASUVORK5CYII=') no-repeat;
}
#output {
    height: 13px;
    width: 13px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    visibility: visible;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA0AAAANCAYAAABy6+R8AAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLCQYlB4EnoCoAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABCklEQVQoz42Rv0oDQRCHv4MJbCCFgoUHvkBKXyGPEDufwUokhdFGokWwt7IRbISkTHmvcIUPcUKK3eJgp1g4i92Ld4gxWw2z329+8ye7vF81pcs59J0fVUjpcsZaHCwq3QQBD8Ddcv6vYDF7BDxCACMGgPFov8iIgQACChKTVw9rvCrqPK52+FopPjodCBA0Og1H0el5Nv1VXcNPPDQGHAhBMcnpZrlOJFi1aK1orXhVNm/zxGnbXnRaXE//nEchcm17ZpB+ggImIaaDx9gMaBfhQeD95bVfWiLQi4W48vzkmM1ndfBx89OczG5t001ePBVUX1UPWt1OesLMbm2zA4Puhk3n7LucxQLfZ4Norb3ftQMAAAAASUVORK5CYII=') no-repeat;
}
#backView {
    height: 13px;
    width: 16px;
    display: inline-block;
    position: relative;
    margin: 1px 10px 1px 10px;
    visibility: visible;
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAANCAYAAACgu+4kAAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwc3GnvIoSQAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABH0lEQVQoz5WToU4DQRCGvzZLspUn+wpIJLLyJDiQEBLMJZDAA1QgTlRUU1tXCY+A5BGoPHFiKkhukm4yiD3CNr3S9k8mu/l3Z+af2dme1GJ04HM6hBPP2f2S/9DvIj/KjNG4gUbZh36Xc15GURr2B3Abzi8Z+cQABTyqkdu8BTjP+WMV91KLSS22eMDMGjOrzNZfZuvKzCSxJlnN3p4wqcVclD3kYlJBWEJQCACrJLNvVwXnwWVoSErQbwUEwgq0+ZMa2JYfBuAV1aSJo7EwvzsFmpj515y2/WjNKbim5SN66Ry8P2dcTRexDGBWXDNwHSoc5GPZfoW8FGZFxs30FYCBi9xRc3BZCrPiNoZ2/rg5SIPMi+GO0030dv2FQ/EDM7CYPt7DsBMAAAAASUVORK5CYII=') no-repeat;
}
 
.breakpoint {
    float: left;
    margin-right: 1em;
    width: auto;
    height: 13px;
    background: #efefef;
    display: block;
    position: relative;
    overflow: hidden;
    margin: 2px solid black;
}
.normal {
    background: transparent;
}
.selected {
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA0AAAANCAYAAABy6+R8AAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYQB8PJFa8AAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAABNElEQVQoz4WPsUsCYRjGf8YNNzTo7vJBDgUNHTSc4KBbro6ORy5KU5tN0VhjS+FfEDXaEFxbY9cQuQSfQ3BCwyck3At9YIOieZo98MAL7/vjed6MMWYMcH6aQxUqePsBaqvKOm0A6PcQpaDoh4QPdcL7E8xQr4EsmC9BLKg8NA8hm72kc+Xx9Hi2GhIryMgAIHZibw9aDZDRBZ3rIs9RZ7neTDK3C1RKUC336L0cc3tTR/fDFGTByLLdTagegMp36d7VAHAWQuSPzy0Mh5BM987vxSoojiGKQBUCgkYzBQGSzOdkBNEr4Pj45QBvtza7XkhCILGg+6A1FEttPL+F67oL6RPIcZFveJse72wHBEdtctncyhcz5tOM40GM1iHxR4jnN1F5L1U8DRkzFhGwTGo4/KsfXjWDCQ6TmRMAAAAASUVORK5CYII=') no-repeat;
}
.breakpointSet {
    background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA0AAAANCAYAAABy6+R8AAAAAXNSR0IArs4c6QAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB9kLBwYOGJqAJ4UAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAA+0lEQVQoz5XSMUvDUBSG4bcXhyM4pFsudBE6GEfB5UKGZLOjq5uCIE7iPyjdxEXQoZC/4OgipIOQbqZDMW6OcbsZBO8Q0MEWkWJJ3vl7lsPpWGu/WOQqy8s44X2S8ukcmyL4Uczu6QnidZczOktUPqY8HB/Bh2OlLWEwTtAHg19kXwvuQwM1/7cBh08Z3Z0ABZCena8HAPViB6hymlHNcppUzXLKaYaq5gVtquYFitq1QtQO5fWDVsbrBygdGqTnNwLS89GhQSFCfJc0QvHNLYj8nFyHBjMcrQVmOEJH8d+PALD5M/nVNW+TFJwDEbajmODiEr2/t/pGbfoGcP1aToLr9OAAAAAASUVORK5CYII=') no-repeat;
}

.linenumber {
    width: 3em;
    height: inherit;
    position: relative;
    display: block;
    text-align: right;
    margin-right: 5px;
    float: right;
    color: black;
}
.currentlineIndicator {
    width: 13px;
    height: 13px;
    position: relative;
    display: inline-block;
}
.breakpointIndicator {
    width: 13px;
    height: 13px;
    position: relative;
    display: inline-block;
}
</style>

<script type="text/javascript">
var http = createRequestObject();
var httpCtrl = createRequestObject();
var serverID = <?php echo pdb_JSDebuggerClient::getServerID(); ?>;
var scriptName = "<?php echo urlencode($_SERVER['SCRIPT_FILENAME']); ?>";
var currentScriptName = "";
var cwd = "<?php echo urlencode(getcwd()); ?>";
var debuggerURL = "<?php echo pdb_JSDebuggerClient::getDebuggerURL(); ?>";
var date = "<?php echo gmdate( 'D, d M Y H:i:s').' GMT'; ?>";

function getServerID() {
    return serverID;
}
function setServerID(port) {
    serverID = port;
}
function createRequestObject() {
    var req;
    var browser = navigator.appName;
    if (browser ==" Microsoft Internet Explorer") {
	req = new ActiveXObject("Microsoft.XMLHTTP");
    } else {
	req = new XMLHttpRequest();
    }
    return req;
}
function doCmd(text) {
    switch(text.cmd) {
    case 'stepNext': 
    case 'stepOver': 
    case 'go':
    case 'switchView':
    case 'backView':
    case 'begin':
    case 'stepOut':		getStatusCB(text); break;

    case 'output':		showOutputCB(text); break;

    case 'status':		showStatusCB(text); break;

    case 'extendedStatus': showExtendedStatusCB(text); break;

    case 'setBreakpoint':	setBreakpointCB(text); break;

    case 'unsetBreakpoint':	unsetBreakpointCB(text); break;

    case 'end':			endCB(text); break;

    default: alert("illegal cmd: " + text.cmd); break;
    }
}
function sendCmd(cmd) {
    var url = debuggerURL;
    data = cmd+"&serverID="+getServerID();
    http.open("POST", url, true);
    http.setRequestHeader("Cache-Control", "no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
    http.setRequestHeader("Last-Modified", date);
    http.setRequestHeader("Pragma", "no-cache");
    http.setRequestHeader("Expires", "Thu, 19 Nov 1981 08:52:00 GMT");
    http.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
    http.setRequestHeader("Content-Length", data.length);
    http.setRequestHeader("XPDB-DEBUGGER", 0);
    http.onreadystatechange = function() {
	 if(http.readyState == 4 && http.status == 200) {
	    doCmd(eval(http.responseText));
	 }
    }
    http.send(data);
}
function startServer() {
    var url = debuggerURL;
	data = "<?php echo pdb_JSDebuggerClient::getPostData(); ?>";
	method = "<?php echo $_SERVER['REQUEST_METHOD']; ?>";
    httpCtrl.open(method, url, true);
    httpCtrl.setRequestHeader("Cache-Control", "no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
    httpCtrl.setRequestHeader("Last-Modified", date);
    httpCtrl.setRequestHeader("Pragma", "no-cache");
    httpCtrl.setRequestHeader("Expires", "Sat, 1 Jan 2005 00:00:00 GMT");
    httpCtrl.setRequestHeader("Content-Length", data.length);
    httpCtrl.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
    httpCtrl.setRequestHeader("XPDB-DEBUGGER", getServerID());
    httpCtrl.onreadystatechange = function() {
	 if(httpCtrl.readyState == 4 && httpCtrl.status == 200) {
	     // alert("debugger exited. Debugger debug output: " +httpCtrl.responseText);
	    document.getElementById("code").innerHTML = httpCtrl.responseText;
	 }
    }
    httpCtrl.send(data);
}
function stepNext() {
    sendCmd("cmd=stepNext");
}
function stepOver() {
    sendCmd("cmd=stepOver");
}
function stepOut() {
    sendCmd("cmd=stepOut");
}
function getStatusCB(cmd) {
    setServerID(cmd.serverID);
	sendCmd("cmd=status");
}

function loaded() {
    startServer();
    sendCmd("cmd=begin&scriptName="+scriptName+"&cwd="+cwd);
}
function toggleBreakpoint(el, event) {
    sendCmd("cmd=toggleBreakpoint&breakpoint="+el.id);
    return false;
}
function setBreakpointCB(cmd) {
    setServerID(cmd.serverID);
    document.getElementById(cmd.breakpoint).lastChild.className="breakpointIndicator breakpointSet";
}
function unsetBreakpointCB(cmd) {
    setServerID(cmd.serverID);
    document.getElementById(cmd.breakpoint).lastChild.className="breakpointIndicator normal";
}
function showOutputCB(cmd) {
    setServerID(cmd.serverID);
    currentScriptName = "";
    document.getElementById("code").innerHTML = cmd.output;
}

function doShowStatusCB(cmd) {
    lines = document.getElementsByClassName("currentlineIndicator");
    for (i=0; i<lines.length; i++) {
	line = lines[i];
	line.className = "currentlineIndicator normal";
    }
    for (i=0; i<cmd.breakpoints.length; i++) {
	breakpoint = cmd.breakpoints[i];
	document.getElementById(breakpoint).lastChild.className="breakpointIndicator breakpointSet";
    }

    currentLine = document.getElementById("bp_"+(cmd.line)).firstChild;
	currentLine.className="currentlineIndicator selected";
	codeArea = document.getElementById("codeArea");
	codeLine = currentLine.parentNode;
	if ((codeLine.offsetTop - codeArea.scrollTop > codeArea.offsetHeight) ||
		(codeLine.offsetTop - codeLine.offsetHeight - codeArea.scrollTop <= 0)) {
	  currentLine.scrollIntoView(true);
	}
}
function showStatusCB(cmd) {
    setServerID(cmd.serverID);
	if (currentScriptName == cmd.scriptName)
	  doShowStatusCB(cmd);
	else
	  sendCmd("cmd=extendedStatus"); // another round-trip 
}
function showExtendedStatusCB(cmd) {
    setServerID(cmd.serverID);

	currentScriptName = cmd.scriptName;
	document.getElementById("code").innerHTML = cmd.script;
	document.title = cmd.scriptName;

	doShowStatusCB(cmd);
}

function stepInto(el, event) {
    sendCmd("cmd=stepNext");
    return false;
}
function stepOver(el, event) {
    sendCmd("cmd=stepOver");
    return false;
}
function stepOut(el, event) {
    sendCmd("cmd=stepOut");
    return false;
}
function end(el, event) {
    sendCmd("cmd=end");
    return false;
}
function endCB(cmd) {
    setServerID(cmd.serverID);
    document.getElementById("code").innerHTML = "";
    document.title = "PDB";
}
function go(el, event) {
    sendCmd("cmd=go");
    return false;
}
function switchView(data) {
    sendCmd("cmd=switchView&scriptName="+data);
    return false;
}
function backView(el, event) {
    sendCmd("cmd=backView");
    return false;
}
function output(el, event) {
    sendCmd("cmd=output");
    return false;
}

function mousemove(el, event) {
}
function trim(str) {
    return str.replace(/^\s*/, "").replace(/\s*$/, "");
}
function mousedown(el, event) {
    if (event.target.parentNode.className=="breakpoint") return true;
    if (event.target && event.target.firstChild && event.target.firstChild.data) {
	switchView(trim(event.target.firstChild.data));
    }
   return false;
}
</script>

<noscript>
Please enable JavaScript in your browser.
</noscript>

</head>
<body>

<div id="navigationBar">
<a id="run" onmousedown="return go(this, event);"></a>
<a id="terminate" onmousedown="return end(this, event);"></a>
<a id="stepInto" onmousedown="return stepInto(this, event);"></a>
<a id="stepOver" onmousedown="return stepOver(this, event);"></a>
<a id="stepOut" onmousedown="return stepOut(this, event);"></a>
<a id="output" onmousedown="return output(this, event);"></a>
<a id="backView" onmousedown="return backView(this, event);"></a>
</div>
<div id="codeArea" style="height:97%;width:97%;overflow:auto;">
  <div style="height:100%;width:10000px;" onmousemove="return mousemove(this, event);" onmousedown="return mousedown(this, event);" id="code"><span>loading...</span></div>
</div>

</body>
<script type="text/javascript">
  loaded();
</script>
</html>
<?php
  exit (0);
} elseif (($serverID = $_SERVER['HTTP_XPDB_DEBUGGER']) != "0") {
/* * The back end part, invoked from JavaScript using a json call. $serverID is a uniq ID generated from JavaScript * */
  header("Expires: Sat, 1 Jan 2005 00:00:00 GMT");
  header("Last-Modified: ".gmdate( "D, d M Y H:i:s")." GMT");
  header("Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
  header("Pragma: no-cache");
  header("Content-Encoding: identity");
  $dbg = new pdb_JSDebugger((int)$serverID);
  $dbg->handleRequests();
  pdb_Logger::debug("SERVER TERMINATED!");
  exit(0);
} else {
/* * The front end part, invoked from JavaScript using json calls * */
  header("Expires: Sat, 1 Jan 2005 00:00:00 GMT");
  header("Last-Modified: ".gmdate( "D, d M Y H:i:s")." GMT");
  header("Cache-Control: no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
  header("Pragma: no-cache");
  header("Content-Encoding: identity");
  pdb_JSDebuggerClient::handleRequest ($_POST);
  exit(0);
}
?>