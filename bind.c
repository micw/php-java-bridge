/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* fcntl */
#include <fcntl.h>

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

void java_get_server_args(struct cfg*cfg, char*env[N_SENV], char*args[N_SARGS]) {
  char *s, *p;
  char*program=cfg->java;
  char*cp=cfg->classpath;
  char*lib_path=cfg->ld_library_path;
  char*home=cfg->java_home;

  args[0]=strdup(program);
  s="-Djava.library.path=";
  p=malloc(strlen(s)+strlen(lib_path)+1);
  strcpy(p, s); strcat(p, lib_path);
  args[1] = p;					/* library path */
  s="-Djava.class.path=";
  p=malloc(strlen(s)+strlen(cp)+1);
  strcpy(p, s); strcat(p, cp);
  args[2] = p;					/* user classes */
  args[3] = strdup("-Djava.awt.headless=true");
  s="-Djava.home=";
  p=malloc(strlen(s)+strlen(home)+1);
  strcpy(p, s); strcat(p, home);
  args[4] = p;					/* java home */
  args[5] = strdup("JavaBridge");
  args[6] = strdup(cfg->sockname);
  args[7] = strdup(cfg->logLevel);
  args[8] = strdup(cfg->logFile);
  args[9] = NULL;

  s="JAVA_HOME=";
  p=malloc(strlen(s)+strlen(home)+1);
  strcpy(p, s); strcat(p, home);
  env[0] = p;					/* java home */

  s="LD_LIBRARY_PATH=";
  p=malloc(strlen(s)+strlen(lib_path)+1);
  strcpy(p, s); strcat(p, lib_path);
  env[1] = p;					/* library path */
  env[2] = NULL;
}
static void exec_vm(struct cfg*cfg) {
  static char*env[N_SENV];
  static char*args[N_SARGS];
  java_get_server_args(cfg, env, args);
  putenv(env[0]);
  putenv(env[1]);
#ifdef CFG_JAVA_INPROCESS
 {
   extern int java_bridge_main(int argc, char**argv) ;
   java_bridge_main(N_SARGS, args);
 }
#else 
 execv(args[0], args);
#endif
}

/*
 return 0 if user has hard-coded the socketname
*/
static short can_fork() {
#ifndef CFG_JAVA_SOCKET_ANON
  return (java_ini_updated&U_SOCKNAME)==0;
#else
  return 1;						/* ignore the already running JVM and
								   start a new JVM with the anonymous
								   socket */
#endif
}

void java_start_server(struct cfg*cfg) {
  int pid=0, err=0, p[2], p1[2];
  if(pipe(p)!=-1) {
	if(can_fork()) {
	  if(!(pid=fork())) {		/* daemon */
		close(p[0]);
		if(!fork()) {			/* guard */
		  if(!(pid=fork())) {	/* java */
			setsid();
			close(p[1]);
			exec_vm(cfg); 
			exit(105);
		  }
		  write(p[1], &pid, sizeof pid);
		  waitpid(pid, &err, 0);
		  write(p[1], &err, sizeof err);
		  exit(0);
		} 
		exit(0);
	  }
	  close(p[1]);
	  wait(&err);
	  if((read(p[0], &pid, sizeof pid))!=(sizeof pid)) pid=0;
	}
  }
  cfg->cid=pid;
  cfg->err=p[0];
}

