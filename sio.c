/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * Proxyenv IO decorator which buffers the output.
 */

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
