/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>
#include <errno.h>

/* path and dir separators */
#include "php.h"
#include "zend.h"

#include "php_java.h"

void java_get_server_args(struct cfg*cfg, char*env[2], char*args[9]) {
  char *s, *p;
  char*program=cfg->java;
  char*cp=cfg->classpath;
  char*lib_path=cfg->ld_library_path;
  char*home=cfg->java_home;

  args[0]=strdup(program);
  s="-Djava.library.path=";
  p=malloc(strlen(s)+strlen(lib_path)+1);
  strcpy(p, s); strcat(p, lib_path);
  args[1] = p;	/* library path */
  s="-Djava.class.path=";
  p=malloc(strlen(s)+strlen(cp)+1);
  strcpy(p, s); strcat(p, cp);
  args[2] = p; 	/* user classes */
  args[3] = strdup("-Djava.awt.headless=true");
  args[4] = strdup("JavaBridge");
  args[5] = strdup(cfg->sockname);
  args[6] = strdup(cfg->logLevel);
  args[7] = strdup(cfg->logFile);
  args[8] = NULL;

  s="JAVA_HOME=";
  p=malloc(strlen(s)+strlen(lib_path)+1);
  strcpy(p, s); strcat(p, home);
  env[0] = p;	/* java home */
  env[1] = NULL;
}

static void exec_vm(struct cfg*cfg) {
  static char*env[2];
  static char*args[9];
  java_get_server_args(cfg, env, args);
  putenv(env[0]);
  execv(args[0], args);
}

/*
 return 0 if user has hard-coded the socketname
*/
static short can_fork() {
  return (java_ini_updated&U_SOCKNAME)==0;
}

static int readpid(int fd) {
  int pid=0, c, err;

  for(c=0; c<sizeof pid; c+=err) {
	if((err=read(fd,((char*)&pid)+c, (sizeof pid)-c))<=0) {
	  php_error(E_WARNING, "php_mod_java(%d): %s",93, "Could not read pid, child lost");
	  pid=0;
	  break;
	}
  }
  return pid;
}

void java_start_server(struct cfg*cfg) {
  int pid=0, p[2];

  {
	struct stat buf;
	unlink(cfg->sockname);
	assert(stat(cfg->sockname, &buf));
  }
  if(pipe(p)!=-1) {
	if(can_fork()) {
	  if(!fork()) {
		close(p[0]);
		if(!(pid=fork())) {
		  exec_vm(cfg); 
		  exit(errno&255);
		}
		write(p[1], &pid, sizeof pid); 
		close(p[1]); 
		exit(0);
	  }
	  close(p[1]);
	  pid=readpid(p[0]);
	  close(p[0]);
	}
  }
  cfg->cid=pid;
}

