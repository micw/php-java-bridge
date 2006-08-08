/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * Proxyenv IO decorator which buffers the output.
 */

#ifndef JAVA_SIO_H
#define JAVA_SIO_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <unistd.h>
#include "java_bridge.h"

#define ASYNC_SEND_SIZE 8192

extern ssize_t EXT_GLOBAL(sfwrite)(const void *ptr, size_t length, SFILE *stream);
extern SFILE* EXT_GLOBAL(sfdopen)(proxyenv*env);
extern int EXT_GLOBAL(sfclose)(SFILE *stream);

#endif
