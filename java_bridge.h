/*-*- mode: C; tab-width:4 -*-*/

/** java_bridge.h -- contains utility procedures.

  Copyright (C) 2003-2007 Jost Boekemeier

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


#ifndef JAVA_BRIDGE_H
#define JAVA_BRIDGE_H

#include "php_java.h"

#include <unistd.h>

/* PHP Includes */
#include "php_wrapper.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"


extern short EXT_GLOBAL (option_set_by_user) (short option, int where);
extern void EXT_GLOBAL(update_hosts)(const char*new_value);
extern void EXT_GLOBAL(update_servlet)(const char*new_value);
extern void EXT_GLOBAL(update_socketname)(const char*new_value);
extern void EXT_GLOBAL (init_cfg) (TSRMLS_D);
extern void EXT_GLOBAL(destroy_cfg) (int);

#endif
