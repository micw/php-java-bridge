<?php /*-*- mode: php; tab-width:4 -*-*/

  /* java_Parser.php -- A bridge which either uses a C based
   parser or the pure PHP parser.

   Copyright (C) 2006 Jost Boekemeier

   This file is part of the PHP/Java Bridge.

   The PHP/Java Bridge ("the library") is free software; you can
   redistribute it and/or modify it under the terms of the GNU General
   Public License as published by the Free Software Foundation; either
   version 2, or (at your option) any later version.

   The library is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with the PHP/Java Bridge; see the file COPYING.  If not, write to the
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

require_once("java/SimpleParser.php");
require_once("java/NativeParser.php");

class java_Parser {
  var $ENABLE_NATIVE = true;
  var $DISABLE_SIMPLE= false;
  var $parser;

  function java_Parser($handler) {
    if($this->ENABLE_NATIVE && function_exists("xml_parser_create")) {
      $this->parser = new java_NativeParser($handler);
	  $handler->RUNTIME["PARSER"]="NATIVE";
    } else {
      if($this->DISABLE_SIMPLE) die("no parser");
      $this->parser = new java_SimpleParser($handler);
	  $handler->RUNTIME["PARSER"]="SIMPLE";
    }
  }
  function parse() {
    $this->parser->parse();
  }
  function getData($str) {
	return $this->parser->getData($str);
  }
}
