#ifndef SIO_H
#define SIO_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef HAVE_BROKEN_STDIO		/* MacOS X and all Solaris versions */

#include <stdio.h>

typedef struct {
  short eof:1;
  short err:1;
  int file;
} SFILE;

extern size_t sfwrite(const  void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream);
extern size_t sfread(void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream);
extern SFILE* sfdopen(int fd, char*flags);
extern int sfclose(SFILE *stream);
#define SFREAD sfread
#define SFWRITE sfwrite
#define SFDOPEN sfdopen
#define SFCLOSE sfclose
#define SFEOF(peer) (peer->eof) 
#define SFERROR(peer) (peer->err)
#else
				/* Linux, BSD, ... */
#define SFILE FILE
#define SFWRITE fwrite
#define SFREAD  fread
#define SFDOPEN fdopen
#define SFCLOSE fclose
#define SFEOF   feof
#define SFERROR ferror
#endif

#endif
