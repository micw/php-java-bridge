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

#ifdef NDEBUG
# include "php.h"
# ifndef NDEBUG
#  warning php.h undefines NDEBUG. Please report this PHP bug. An API file must not change NDEBUG
#  define NDEBUG 1
# endif
#else
# include "php.h"
# ifdef NDEBUG
#  warning php.h defines NDEBUG. Please report this PHP bug. An API file must not change NDEBUG
#  undef NDEBUG
# endif
#endif
