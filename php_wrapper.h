#ifndef PHP_WRAPPER_H
#define PHP_WRAPPER_H

#include "php.h"

/* 
PHP 5.0.1 defines the following nonsense:

#if HAVE_ASSERT_H
#if PHP_DEBUG
#undef NDEBUG
#else
#ifndef NDEBUG
#define NDEBUG
#endif
#endif

Revert it!
*/

#undef NDEBUG
#ifndef JAVA_COMPILE_DEBUG
#define NDEBUG
#endif

#endif
