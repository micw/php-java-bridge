/*-*- mode: C; tab-width:4 -*-*/

/* sio.c -- proxyenv IO decorator which can buffer the output.

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

#include "zend.h"
#include "init_cfg.h"
#if !defined(ZEND_ENGINE_2)

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#include "sio.h"


struct sfile {
  char *buf;
  short err:1;
  size_t len;
  proxyenv *env;
};

static ssize_t swrite(SFILE*stream, const void*buf, size_t length) {
  ssize_t n;
  if(!length) 
	return 0;
 res:
  errno = 0;
  n = (*(*stream->env)->async_ctx.f_send)(stream->env, buf, length);
  if(!n && errno==EINTR) goto res; // Solaris, see INN FAQ
  return (n<=0) ? -1 : n;
}
static short sflush(SFILE *stream) {
  size_t i, len = stream->len;
  ssize_t n;
  for(i=0; (n=swrite(stream, stream->buf+i, len-i))>0; i+=n);
  return(n!=-1);
}
/**
 * Write a buffer to the buffered output stream. The implementation
 * uses the proxyenv async_ctx.f_send(proxyenv, ...) to send the data.
 * @param buf The data
 * @param length The data length
 * @param stream The buffered output stream
 */
ssize_t EXT_GLOBAL(sfwrite)(const void *buf, size_t length, SFILE *stream) {
  size_t i, len = stream->len;
  ssize_t n;
  if(length+len<=ASYNC_SEND_SIZE) {
	memcpy(stream->buf+len, buf, length);
	stream->len+=length;
	return (ssize_t)length;
  }
  if(!sflush(stream)) return -1;

  for(n=i=0; (length-i)>=ASYNC_SEND_SIZE && 
		(n=swrite(stream, buf+i, length-i))>0; i+=n)
	;
  if(n==-1) return -1;
  memcpy(stream->buf, buf+i, stream->len=(length-i));
  
  return (ssize_t)length;
}

/**
 * Open a buffered output stream for the env and return a handle. The
 * implementation uses the proxyenv async_ctx.f_send(proxyenv, ...) to
 * send the data.  
 * @param env The proxy environment.
 */
SFILE* EXT_GLOBAL(sfdopen)(proxyenv*env) {
  SFILE*f = calloc(1, sizeof*f);
  if(f) {
	f->env = env;
    f->buf = malloc(ASYNC_SEND_SIZE);
    if(!f->buf) { free (f); f = 0; }
  }
  return f;
}
/**
 * Flushes the buffered output stream and releases all associated
 * data. The underlying data structure is not closed.
 * @param *f The handle
 * @return EOF on error, 0 otherwise.
 */
int EXT_GLOBAL(sfclose)(SFILE *f) {
  short success = sflush(f);
  free(f->buf);
  free(f);
  return !success ? EOF : 0;
}
#endif
