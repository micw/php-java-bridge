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


void java_start_server(struct cfg*cfg) {
  int pid;
  if(!(pid=fork())) {
	exec_vm(cfg);
	exit(errno&255);
  }
  cfg->cid=pid;
}

