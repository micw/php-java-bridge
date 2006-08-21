/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * This file contains the API implementation.

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
  exception statement from your version.  */

#ifndef PHP_JAVA_API_H
#define PHP_JAVA_API_H

#include "java_bridge.h"

extern short EXT_GLOBAL(last_exception_get)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(last_exception_clear)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(set_file_encoding)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(require)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(instanceof) (INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(session)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(context)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(reset)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(begin_document)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(end_document)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(values)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(serialize)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(deserialize)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(get_closure)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(inspect) (INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(construct_class)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(call)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(toString)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(set)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(get)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(offsetExists) (INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(offsetGet)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(offsetSet) (INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(offsetUnset)(INTERNAL_FUNCTION_PARAMETERS);
extern short EXT_GLOBAL(construct)(INTERNAL_FUNCTION_PARAMETERS);

#endif
