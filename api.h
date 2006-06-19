/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * This file contains the API implementation.
 */

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
