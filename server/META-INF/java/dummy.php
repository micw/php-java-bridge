<?php /*-*- mode: php; tab-width:4 -*-*/

/*
 * phpdebugger.php version 1.0alpha -- A PHP debugger.
 *
 * Copyright (C) 2009 Jost Boekemeier.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this file (the "Software"), to deal in the
 * Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */


define ("PDB_DEBUG", false);

  /**
   * Quick Installation:
   *
   * Disable/remove ZendDebugger and xdebug debugger (if any).
   *
   * 
   * Example configuration for eclipse:
   *
   * 1. Copy this file to /tmp/phpdebugger.php
   *
   * 2. In the eclipse preferences/php/debug/installed debugger select
   *  "ZendDebugger". Click configure. In the Zend Debugger Settings
   *  dialog type "/tmp/phpdebugger.php" into the "Dummy File Name"
   *  box.
   *
   * 3. Debug your PHP scripts as usual. 
   *
   */

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

class pdb_Logger {
  const FATAL = 1;
  const INFO = 2;
  const VERBOSE = 3;
  const DEBUG = 4;

  private static $logLevel = 0;
  private static $logFileName = "/tmp/pdb_PHPDebugger.inc.log";

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

class pdb_Environment {
  public $filename, $stepNext;
  public $vars, $line;

  public function pdb_Environment($filename, $stepNext) {
    $this->filename = $filename;
    $this->stepNext = $stepNext;
    $this->line = -1;
  }

  public function update ($line, &$vars) {
    $this->line = $line;
    $this->vars = &$vars;
  }
}

abstract class pdb_Message {
  public $session;

  public abstract function getType();

  public function pdb_Message($session) {
    $this->session = $session;
  }

  public function serialize() {
    $this->session->out->writeShort($this->getType());
  }

  private static $messages = array();
  public static function register($message) {
    pdb_Message::$messages[$message->getType()] = $message;
  }
  public function getMessageById($id) {
    $message = pdb_Message::$messages[$id];
    return $message;
  }
  public function getMessage() {
    $id = $this->session->in->readShort();
    $message = $this->getMessageById($id);
    if (!$message) trigger_error("invalid message: $id", E_USER_ERROR);
    $message->deserialize();
    return $message;
  }

  protected function handleContinueProcessFile($message) {
    $this->getMessageById(pdb_FileContentRequest::TYPE)->serialize();
    return false;
  }
  private static function doEval($__pdb_Code) {
    return  eval ("?>".$__pdb_Code);
  }
  protected function handleFileContentResponse($message) {
    if(!$message->status) {
      $code = $this->session->parseCode($this->currentFrame->filename, $message->script);
      if (PDB_DEBUG) echo "parse file:::" . $code ."\n";
      if (!PDB_DEBUG) ob_start();
      self::doEval ($code);
      $output = $this->getMessageById(pdb_OutputNotification::TYPE);
      if(!PDB_DEBUG) $output->setOutput(ob_get_contents());
      if(!PDB_DEBUG) ob_end_clean();
      $output->serialize();
      $this->status = 42; //FIXME
      $this->getMessageById(pdb_DebugScriptEndedNotification::TYPE)->serialize();
    } else {
      trigger_error ("invalid status:: ".$message->status, E_USER_ERROR);
    }
    return true;
  }
  protected function handleStep($message) {
    return false;
  }
  protected function handleGo($message) {
    foreach ($this->session->environments as $frame) {
      $frame->stepNext = false;
    }
    return true; // exit
  }
  public function handleRequests () {
	$this->ignoreInterrupt = false;

    $this->serialize();
    while(1) {
      $message = $this->getMessage();
      switch ($message->getType()) {
      case pdb_SetProtocolRequest::TYPE:
	$message->ack();
	break;
      case pdb_StartRequest::TYPE:
	$message->ack();
	$this->getMessageById(pdb_StartProcessFileNotification::TYPE)->serialize();
	break;
      case pdb_ContinueProcessFileNotification::TYPE:
	if ($this->handleContinueProcessFile($message)) return pdb_ContinueProcessFileNotification::TYPE;
	break;
      case pdb_FileContentResponse::TYPE:
	if ($this->handleFileContentResponse($message)) return pdb_FileContentResponse::TYPE;
	break;
      case pdb_AddBreakpointRequest::TYPE:
	$message->ack();
	break;
      case pdb_GetCallStackRequest::TYPE:
	$message->ack();
	break;
      case pdb_GetCWDRequest::TYPE:
	$message->ack();
	break;
      case pdb_GetVariableValueRequest::TYPE:
	$message->ack();
	break;
      case pdb_GoRequest::TYPE:
	$message->ack();
	if ($this->handleGo($message)) return pdb_GoRequest::TYPE;
	break;
      case pdb_StepOverRequest::TYPE:
	$message->ack();
	if ($this->handleStep($message)) return pdb_StepOverRequest::TYPE;
	break;
      case pdb_StepIntoRequest::TYPE:
	$message->ack();
	if ($this->handleStep($message)) return pdb_StepIntoRequest::TYPE;
	break;
      case pdb_StepOutRequest::TYPE:
	$message->ack();
	if ($this->handleStep($message)) return pdb_GoRequest::TYPE; // until stepNext of parent frame
	break;
      default: trigger_error("protocol error: $message", E_USER_ERROR);
      }
    }
  }
}
abstract class pdb_MessageRequest extends pdb_Message {
  public abstract function ack();
}

class pdb_DebugSessionStart extends pdb_Message {
  const TYPE = 2005;

  public $status;

  public $uri;
  public $query;
  public $options;
  
  public $in, $out;

  public $lines;
  public $breakpoints;

  public $environments, $currentFrame;

  public $ignoreInterrupt; 

  public function getType() {
    return self::TYPE;
  }
  public function pdb_DebugSessionStart($filename, $uri, $query, $options) {
    parent::pdb_Message($this);
    $this->uri = $uri;
    $this->query = $query;
    $this->options = $options;
    $this->breakpoints = $this->lines = array();

    $this->environments = array ($this->currentFrame = new pdb_Environment($filename, false));
	$this->ignoreInterrupt = false;

    $errno = 0; $errstr = "";
    $io = fsockopen("127.0.0.1", 10137, $errno, $errstr, 5) or trigger_error("fsockopen", E_USER_ERROR);
    $this->in =new pdb_In($io);
    $this->out=new pdb_Out($io);
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt(2004102501);
    $out->writeString($this->currentFrame->filename);
    $out->writeString($this->uri);
    $out->writeString($this->query);
    $out->writeString($this->options);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }

  public function hasBreakpoint($scriptName, $line) {
    static $first = true;

    if ($this->currentFrame->stepNext) return true;

    foreach ($this->breakpoints as $breakpoint) {
      if($breakpoint->type==1) {
	if ($first && $breakpoint->file==$scriptName&&$breakpoint->line==-1) {
	  $first = false; return true;
	}
	if ($breakpoint->file==$scriptName&&$breakpoint->line==$line) return true;
      }
    }

    return false;
  }
  function parseCode($filename, $contents) {
	$parser = new pdb_Parser($filename, $contents);
	return $parser->parseScript();
  }

  public function __toString() {
    return "pdb_DebugSessionStart: {$this->currentFrame->filename}";
  }
}


class pdb_HeaderOutputNotification extends pdb_Message {
  const TYPE = 2008;
  private $out;

  public function setOutput($out) {
    $this->out = $out;
  }
  protected function getAsciiOutput() {
    return $this->out;
  }
  protected function getEncodedOutput () {
    return $this->out; //FIXME
  }
  protected function getOutput() {
    return $this->getAsciiOutput();
  }
  public function getType() {
    return self::TYPE;
  }

  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeString($this->getOutput());
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_HeaderOutputNotification: ".$this->getOutput();
  }
}

class pdb_OutputNotification extends pdb_HeaderOutputNotification {
  const TYPE = 2004;

  public function getType() {
    return self::TYPE;
  }
  protected function getOutput() {
    return $this->getEncodedOutput();
  }
  public function __toString () {
    return "pdb_OutputNotification: ".$this->getAsciiOutput();
  }
}

class pdb_DebugScriptEndedNotification extends pdb_Message {
  const TYPE = 2002;

  public function getType() {
    return self::TYPE;
  }

  public function serialize() {
    if (PDB_DEBUG) echo "XXX SER";
    $out = $this->session->out;
    parent::serialize();
    $out->writeShort($this->session->status);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_DebugScriptEndedNotification: {$this->session->status}";
  }
}


class pdb_ReadyNotification extends pdb_Message {
  const TYPE = 2003;
  
  public function getType() {
    return self::TYPE;
  }

  protected function handleStep($message) {
    return true;
  }

  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeString($this->session->currentFrame->filename);
    $out->writeInt($this->session->currentFrame->line);
    $out->writeInt(0);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_ReadyNotification: ";
  }
}

class pdb_SetProtocolRequest extends pdb_MessageRequest {
  const TYPE = 10000;
  public $id;
  public $protocolId;
  
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    $this->protocolId = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_SetProtocolResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_SetProtocolRequest: ";
  }
}

class pdb_SetProtocolResponse extends pdb_Message {
  const TYPE = 11000;
  private $req;
  
  public function pdb_SetProtocolResponse ($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt($this->req->protocolId);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_SetProtocolResponse: ";
  }
}

class pdb_StartRequest extends pdb_MessageRequest {
  const TYPE = 1;
  public $id;
  public $protocolId;
  
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }

  public function ack() {
    $res = new pdb_StartResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_StartRequest: ";
  }
}

class pdb_StartResponse extends pdb_Message {
  const TYPE = 1001;
  private $req;
  
  public function pdb_StartResponse ($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_StartResponse: ";
  }
}
class pdb_StartProcessFileNotification extends pdb_Message {
  const TYPE = 2009;
  public function pdb_StartProcessFileNotification ($session) {
    parent::pdb_Message($session);
  }
  protected function handleContinueProcessFile($message) {
    return true; // next
  }
  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeString($this->session->currentFrame->filename);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_StartProcessFileNotification: {$this->session->currentFrame->filename}";
  }
}

class pdb_ContinueProcessFileNotification extends pdb_Message {
  const TYPE = 2010;
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_ContinueProcessFileNotification: ";
  }
}

class pdb_FileContentRequest extends pdb_Message {
  const TYPE = 10001;
  protected $id;

  private static function getId() {
    static $id = 0;
    return ++$id;
  }
  public function getType() {
    return self::TYPE;
  }

  public function  pdb_FileContentRequest ($session) {
    parent::pdb_Message($session);
    $this->id = pdb_FileContentRequest::getId();
  }
  protected function doSerialize($out) {
    parent::serialize();
    $out->writeInt($this->id);
    $out->writeString($this->session->currentFrame->filename);
  }
  public function serialize() {
    $out = $this->session->out;
    $this->doSerialize($out);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }    
  public function __toString () {
    return "pdb_FileContentRequest: ";
  }
}

class pdb_FileContentExtendedRequest extends pdb_FileContentRequest {
  const TYPE = 10002;
  const BASE = 65521;

  public function getType() {
    return self::TYPE;
  }
  protected function handleFileContentResponse($message) {
    return true; // next
  }    
  public function adler32($data) {
    $s1 = 1; 
    $s2 = 0; 
    $len = strlen($data);

    for($idx = 0; $len-->0; ) {
      $s1 = ($s1 + ord($data[$idx++])) % self::BASE;
      $s2 = ($s2 + $s1) % self::BASE;
    }
    return ($s2 << 16) | $s1;
  }

  public function serialize() {
    $data = file_get_contents($this->session->currentFrame->filename);
    $out = $this->session->out;
    parent::doSerialize($out);
    $out->writeInt(strlen($data));
    $out->writeInt($this->adler32($data));
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_FileContentExtendedRequest: {$this->name}";
  }
}

class pdb_FileContentResponse extends pdb_Message {
  const TYPE = 11001;
  public $script;
  public $id;
  public $status;

  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    $this->status = $in->readInt();
    $this->script = $in->readString();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_FileContentResponse: {$this->script}";
  }
}

class pdb_Breakpoint {
  public $type, $lifeTime, $file, $line, $condition;
  private $id;

  public function pdb_Breakpoint($type, $lifeTime, $file, $line, $condition, $id) {
    $this->type = $type;
    $this->lifeTime = $lifeTime;
    $this->file = $file;
    $this->line = $line;
    $this->condition = $condition;
    $this->id = $id;
  }
  public function __toString () {
    return "pdb_Breakpoint: ";
  }
}
class pdb_AddBreakpointResponse extends pdb_Message {
  const TYPE = 1021;
  private $req;
  private $id;

  private static function getId() {
    static $id = 0;
    return ++$id;
  }

  public function pdb_AddBreakpointResponse($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
    $this->id = self::getId();
    $this->session->breakpoints[] = new pdb_Breakpoint($req->type, $req->lifeTime, $req->file, $req->line, $req->condition, $this->id);
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    $out->writeInt($this->id);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_AddBreakpointResponse: {$this->id}";
  }
}

class pdb_AddBreakpointRequest extends pdb_MessageRequest {
  const TYPE = 21;
  public $id;
  public $type;
  public $lifeTime;

  public $file;
  public $line;

  public $condition;

  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    $this->type = $in->readShort();
    $this->lifeType = $in->readShort();
    switch($this->type) {
    case 1: 
      $this->file = $in->readString();
      $this->line = $in->readInt();
      break;
    case 2:
      $this->condition = $in->readString();
      break;
    default: 
      trigger_error("invalid breakpoint", E_USER_ERROR);
    }
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_AddBreakpointResponse ($this);
    $res->serialize();
  }
  public function __toString () {
    if ($this->type == 1) 
      return "pdb_AddBreakpointRequest: {$this->file}, {$this->line}";
    else
      return "pdb_AddBreakpointRequest: {$this->condition}";
  }
}

class pdb_GetCallStackResponse extends pdb_Message {
  const TYPE = 1034;
  private $req;

  public function pdb_GetCallStackResponse($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $n = count($this->session->environments);
    $out->writeInt($n);
    $out->writeInt(0);
    $out->writeInt(-1);
    $out->writeInt(0);

    for ($i=0; $i<$n; $i++) {
      if ($i>0) {
	$out->writeString($this->session->environments[$i-1]->filename);
	$out->writeInt(/*$this->session->environments[$i-1]->line*/1); //FIXME
	$out->writeInt(0);
      }
      $out->writeString($this->session->environments[$i]->filename);
      $out->writeInt(/*$this->session->environments[$i]->line*/1); //FIXME
      $out->writeInt(0);

      $out->writeInt(0);
    }
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_GetCallStackResponse: ";
  }
}
class pdb_GetCallStackRequest extends pdb_MessageRequest {
  const TYPE = 34;
  public $id;
	
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_GetCallStackResponse ($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_GetCallStackRequest: ";
  }
}


class pdb_GetCWDResponse extends pdb_Message {
  const TYPE = 1036;
  private $req;

  public function pdb_GetCWDResponse ($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    $out->writeString(getcwd());    
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_GetCWDResponse: ";
  }
}

class pdb_GetCWDRequest extends pdb_MessageRequest {
  const TYPE = 36;
  public $id;

  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_GetCWDResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_GetCWDRequest: ";
  }
}

class pdb_GetVariableValueResponse extends pdb_Message {
  const TYPE = 1032;
  private $req;

  public function pdb_GetVariableValueResponse ($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    if (PDB_DEBUG) echo "evalcode:::".$this->req->code."\n";
    if ($this->req->code[0]=='$') 
      $out->writeString(serialize($this->session->currentFrame->vars[substr($this->req->code, 1)]));
    else
      $out->writeString(serialize($this->session->currentFrame->vars));
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_GetVariableValueResponse: ";
  }
}

class pdb_GetVariableValueRequest extends pdb_MessageRequest {
  const TYPE = 32;
  public $id;
  public $code;
  public $depth;
  public $paths;

  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    $this->code = $in->readString();
    $this->depth = $in->readInt();

    $paths = array();
    $length = $in->readInt();
    while($length--) {
      $this->paths[] = $in->readString();
    }
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_GetVariableValueResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_GetVariableValueRequest: ";
  }
}

class pdb_StepOverResponse extends pdb_Message {
  const TYPE = 1012;
  private $req;

  public function pdb_StepOverResponse($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->req->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_StepOverResponse: ";
  }
}

class pdb_StepOverRequest extends pdb_MessageRequest {
  const TYPE = 12;
  public $id;
  
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_StepOverResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_StepOverRequest: ";
  }
}

class pdb_StepIntoResponse extends pdb_StepOverResponse {
  const TYPE = 1011;
  public function getType() {
    return self::TYPE;
  }
  public function __toString () {
    return "pdb_StepIntoResponse: ";
  }
}

class pdb_StepIntoRequest extends pdb_StepOverRequest {
  const TYPE = 11;
  public function getType() {
    return self::TYPE;
  }
  public function ack() {
    $res = new pdb_StepIntoResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_StepIntoRequest: ";
  }
}

class pdb_StepOutResponse extends pdb_StepOverResponse {
  const TYPE = 1013;
  public function getType() {
    return self::TYPE;
  }
  public function __toString () {
    return "pdb_StepOutResponse: ";
  }
}

class pdb_StepOutRequest extends pdb_StepOverRequest {
  const TYPE = 13;
  public function getType() {
    return self::TYPE;
  }
  public function ack() {
    $res = new pdb_StepOutResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_OutIntoRequest: ";
  }
}

class pdb_GoResponse extends pdb_Message {
  const TYPE = 1014;
  private $req;

  public function pdb_GoResponse ($req) {
    parent::pdb_Message($req->session);
    $this->req = $req;
  }

  public function getType() {
    return self::TYPE;
  }
  public function serialize() {
    $out = $this->session->out;
    parent::serialize();
    $out->writeInt($this->req->id);
    $out->writeInt(0);
    $out->flush();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function __toString () {
    return "pdb_GoResponse: ";
  }
}

class pdb_GoRequest extends pdb_MessageRequest {
  const TYPE = 14;
  public $id;
 
  public function getType() {
    return self::TYPE;
  }
  public function deserialize() {
    $in = $this->session->in;
    $this->id = $in->readInt();
    if (PDB_DEBUG) echo "$this\n";
  }
  public function ack() {
    $res = new pdb_GoResponse($this);
    $res->serialize();
  }
  public function __toString () {
    return "pdb_GoRequest: ";
  }
}

class pdb_In {
  private $in;
  private $len;

  public function pdb_In($in) {
    $this->in = $in;
    $this->len = 0;
  }
  private function readBytes($n) {
    $str = "";
    while ($n) {
      if (feof($this->in)) trigger_error("read", E_USER_ERROR);

      $s = fread($this->in, $n);
      $n -= strlen($s);

      $str.=$s;
    }
    return $str;
  }
  public function read() {
    if(!$this->len) {
      $str = $this->readBytes(4);
      $lenDesc = unpack("N", $str);
      $this->len = array_pop($lenDesc);
    }
  }
  public function readShort() {
    $this->read();

    $this->len-=2;
    $str = $this->readBytes(2);
    $lenDesc = unpack("n", $str);
    return array_pop($lenDesc);
  }
  public function readInt() {
    $this->read();

    $this->len-=4;
    $str = $this->readBytes(4);
    $lenDesc = unpack("N", $str);
    return array_pop($lenDesc);
  }
  public function readString() {
    $this->read();

    $length = $this->readInt();
    $this->len-=$length;
    return $this->readBytes($length);
  }
  public function __toString () {
    return "pdb_In: ";
  }
}
class pdb_Out {
  private $out;
  private $buf;
  
  public function pdb_Out($out) {
    $this->out = $out;
    $this->buf = "";
  }

  public function writeShort($val) {
    $this->buf.=pack("n", $val);
  }
  public function writeInt($val) {
    $this->buf.=pack("N", $val);
  }
  public function writeString($str) {
    $length = strlen($str);
    $this->writeInt($length);
    $this->buf.=$str;
  }
  public function writeUTFString($str) {
    $this->writeString(urlencode($str));
  }
  public function flush() {
    $length = strlen($this->buf);
    $this->buf = pack("N", $length).$this->buf;
    fwrite($this->out, $this->buf);
    $this->buf = "";
  }
  public function __toString () {
    return "pdb_Out: ";
  }
}
$pdb_me = $_SERVER["PATH_INFO"];
$dbg = new pdb_DebugSessionStart($pdb_me, $pdb_me, $_SERVER["QUERY_STRING"], "&debug_fastfile=1");
pdb_Message::register(new pdb_SetProtocolRequest($dbg));
pdb_Message::register(new pdb_StartRequest($dbg));
pdb_Message::register(new pdb_ContinueProcessFileNotification($dbg));
pdb_Message::register(new pdb_FileContentResponse($dbg));
pdb_Message::register(new pdb_AddBreakpointRequest($dbg));
pdb_Message::register(new pdb_GetCallStackRequest($dbg));
pdb_Message::register(new pdb_GetCWDRequest($dbg));
pdb_Message::register(new pdb_GetVariableValueRequest($dbg));
pdb_Message::register(new pdb_StepOverRequest($dbg));
pdb_Message::register(new pdb_StepIntoRequest($dbg));
pdb_Message::register(new pdb_StepOutRequest($dbg));
pdb_Message::register(new pdb_GoRequest($dbg));

pdb_Message::register(new pdb_StartProcessFileNotification($dbg));
pdb_Message::register(new pdb_FileContentRequest($dbg));
pdb_Message::register(new pdb_FileContentExtendedRequest($dbg));
pdb_Message::register(new pdb_ReadyNotification($dbg));
pdb_Message::register(new pdb_DebugScriptEndedNotification($dbg));
pdb_Message::register(new pdb_HeaderOutputNotification($dbg));
pdb_Message::register(new pdb_OutputNotification($dbg));

function pdb_getDefinedVars($vars1, $vars2) {
  if(isset($vars2)) $vars1['pbd_This'] = $vars2;

  unset($vars1['__pdb_Code']);	     // see pdb_Message::doEval()

  return $vars1;   
}
function pdb_startCall() {
  global $dbg;

  $currentFrame = clone($dbg->currentFrame);

  /* check for stepOver and stepReturn */
  $currentFrame->stepNext = $dbg->currentFrame->stepNext == pdb_StepIntoRequest::TYPE ? pdb_StepIntoRequest::TYPE : false;

  return $currentFrame;
}

function pdb_resolveIncludePath($filename) {
  if (file_exists($filename)) return realpath($filename);
  $paths = get_include_path();
  foreach ($paths as $path) {
    $file="$path/$filename";
    if (file_exists($file)) return realpath($file);
  }
  trigger_error("file $filename not found", E_USER_ERROR);
}
function pdb_startInclude($filename) {
  global $dbg;

  $filename = pdb_resolveIncludePath($filename);
  $stepNext = $dbg->currentFrame->stepNext == pdb_StepIntoRequest::TYPE ? pdb_StepIntoRequest::TYPE : false;
  $dbg->currentFrame = new pdb_Environment($filename, $stepNext);

  /* BEGIN: StartProcessFileNotification */
  $dbg->getMessageById(pdb_StartProcessFileNotification::TYPE)->handleRequests();
  /* ...  set breakpoints ... */
  /* END: ContinueProcessFileNotification */

  $code = $dbg->parseCode(realpath($filename), file_get_contents($filename));

  /* BEGIN: FileContentExtendedRequest */
  $dbg->getMessageById(pdb_FileContentExtendedRequest::TYPE)->handleRequests();
  /* ... get call stack, getcwd .. */
  /* END: FileContentResponse */


  array_push($dbg->environments, $dbg->currentFrame);

  return $code; // eval -> pdb_step/MSG_READY or pdb_endInclude/MSG_READY OR FINISH
}
function pdb_endInclude() {
  global $dbg;

  array_pop($dbg->environments);

  $dbg->currentFrame = end($dbg->environments);
}


function pdb_step($filename, $line, &$vars) {
  global $dbg;
  if ($dbg->ignoreInterrupt) return;

  $dbg->ignoreInterrupt = true;

  // pull the current frame from the stack or the top-level environment
  $dbg->currentFrame = (isset($vars['__pdb_CurrentFrame'])) ? $vars['__pdb_CurrentFrame'] : end($dbg->environments);
  unset($vars['__pdb_CurrentFrame']);

  $dbg->currentFrame->update($line, $vars);

  if ($dbg->hasBreakpoint($filename, $line)) {
    $stepNext = $dbg->getMessageById(pdb_ReadyNotification::TYPE)->handleRequests();
    $dbg->currentFrame->stepNext = $stepNext != pdb_GoRequest::TYPE ? $stepNext : false;
  }
  
  $dbg->ignoreInterrupt = false;

}

$dbg->handleRequests();

?>
