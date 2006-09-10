/*-*- mode: C; tab-width:4 -*-*/

/** parser.h -- a fast parser for the PHP/Java Bridge XML protocol.

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
  exception statement from your version.
 */

#ifndef JAVA_PARSER_H
#define JAVA_PARSER_H

#include "protocol.h"

#define PARSER_GET_STRING(pst, pos) ((*pst[pos].string)+pst[pos].off)
typedef struct {
  size_t length, off;
  unsigned char** string; //address of s (stored in proxyenv)
} parser_string_t;

typedef struct {
  short n;
  parser_string_t *strings;
} parser_tag_t;

typedef struct parser_cb {
  void (*begin)(parser_tag_t[3], struct parser_cb *);
  void (*end)(parser_string_t[1], struct parser_cb *);
  void *ctx;
  proxyenv *env;
} parser_cb_t;

extern short EXT_GLOBAL (parse)(proxyenv *env, parser_cb_t *cb);
extern short EXT_GLOBAL (parse_header) (proxyenv *env, parser_cb_t *cb);

#endif
