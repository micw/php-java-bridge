#include "sio.h"

#ifdef HAVE_BROKEN_STDIO
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>

/*  fix/workaround for broken stdio implementations */

size_t sfwrite(const void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream) {
  size_t c=0, s, n;

  //assert(nmemb>0);
  while(nmemb--) {
    s = n = 0;
    while((size>s)&&((n=write(stream->file, ptr+s+c, size-s)) > 0)) s+=n;
    c += s;
    if(n == -1) { stream->eof = 1; break; }
  }
  return c/size;
}

size_t sfread(void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream) {
  size_t c=0, s, n;

  //assert(nmemb>0);
  while(nmemb--) {
    s = n = 0;
    while((size>s)&&((n=read(stream->file, ptr+s+c, size-s)) > 0)) s+=n;
    c += s;
    if(n == -1) { stream->eof = 1; break; }
  }
  return c/size;
}

SFILE* sfdopen(int fd, char*flags) {
  SFILE*f = calloc(1, sizeof*f);

  if(f)
    f->file = fd;
  else
    { f->err=1; errno=ENOMEM; }

  return f;
}

int sfclose(SFILE *f) {
  int r = close(f->file);
  free(f);
  return r==-1?EOF:0;
}

#endif
