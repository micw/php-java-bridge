/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* stat */
#include <sys/stat.h>

/* fcntl */
#include <fcntl.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* signal */
#include <signal.h>

/* poll */
#include <sys/poll.h>

/* wait */
#include <sys/wait.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>
#include <errno.h>

/* path and dir separators */
#include "php_wrapper.h"
#include "zend.h"

#include "php_java.h"

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

static void java_get_server_args(char*env[N_SENV], char*args[N_SARGS]) {
  static const char separator[2] = {ZEND_PATHS_SEPARATOR, 0};
  char *s, *p;
  char*program=cfg->java;
  char*cp=cfg->classpath;
  char*lib_path=cfg->ld_library_path;
  char*sys_libpath=getenv("LD_LIBRARY_PATH");
  char*home=cfg->java_home;

  if(!sys_libpath) sys_libpath="";
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
  /* disabled due to problems with IBM java, it could not find
	 default mime table anymore */
  //s="-Djava.home=";
  //p=malloc(strlen(s)+strlen(home)+1);
  //strcpy(p, s); strcat(p, home);
  //args[4] = p;					/* java home */
  args[4] = strdup("JavaBridge");
  args[5] = strdup(cfg->sockname);
  args[6] = strdup(cfg->logLevel);
  args[7] = strdup(cfg->logFile);
  args[8] = NULL;

  s="JAVA_HOME=";
  p=malloc(strlen(s)+strlen(home)+1);
  strcpy(p, s); strcat(p, home);
  env[0] = p;					/* java home */

  s="LD_LIBRARY_PATH=";
  p=malloc(strlen(s)+strlen(lib_path)+1+strlen(sys_libpath)+1);
  strcpy(p, s); strcat(p, lib_path); 
  strcat(p, separator); strcat(p, sys_libpath);
  env[1] = p;					/* library path */
  env[2] = NULL;
}

static const char* const wrapper = EXTENSION_DIR/**/"/RunJavaBridge";
static short use_wrapper() {
  struct stat buf;
  short use_wrapper=0;

  if(!stat(wrapper, &buf) && (S_IFREG&buf.st_mode)) {
	if(getuid()==buf.st_uid)
	  use_wrapper=(S_IXUSR&buf.st_mode);
	else if(getgid()==buf.st_gid)
	  use_wrapper=(S_IXGRP&buf.st_mode);
	else 
	  use_wrapper=(S_IXOTH&buf.st_mode);
  }

  return use_wrapper;
}
  
char*java_get_server_string() {
  short must_use_wrapper = use_wrapper();
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  java_get_server_args(env, args);
  if(must_use_wrapper)
	length+=strlen(wrapper)+1;

  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	size_t l;
	if(!args[i]) break;
	l=strlen(args[i]);
	length+=(l?l:2)+1;
  }
  s=malloc(length+1);
  assert(s); if(!s) exit(9);

  *s=0;
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	strcat(s, env[i]); strcat(s, " ");
	free(env[i]);
  }
  if(must_use_wrapper) {
	strcat(s, wrapper);
	strcat(s, " ");
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, args[i]);
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}

static void exec_vm() {
#ifdef CFG_JAVA_INPROCESS
  extern int java_bridge_main(int argc, char**argv) ;
  static char*env[N_SENV];
  static char*args[N_SARGS];
  java_get_server_args(env, args);
  putenv(env[0]);
  putenv(env[1]);
  java_bridge_main(N_SARGS, args);
#else
  static char*env[N_SENV];
  static char*_args[N_SARGS+1];
  char **args=_args+1;
  java_get_server_args(env, args);
  putenv(env[0]);
  putenv(env[1]);
  if(use_wrapper()) *--args = strdup(wrapper);
  execv(args[0], args);
#endif
}

int java_test_server() {
  char term=0;
  int sock;
  int n, c, e;
  jobject ob;

#ifndef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock==-1) return FAILURE;
  n = connect(sock,(struct sockaddr*)&cfg->saddr, sizeof cfg->saddr);
  if(n!=-1) {
	c = read(sock, &ob, sizeof ob);
	c = (c==sizeof ob) ? write(sock, &term, sizeof term) : 0;
  }
  e = close(sock);

  return (n!=-1 && e!=-1 && c==1)?SUCCESS:FAILURE;
}

static int java_wait_server() {
  struct pollfd pollfd[1] = {{cfg->err, POLLIN, 0}};
  int count=15;

  /* wait for the server that has just started */
  while(cfg->cid && (java_test_server()==FAILURE) && --count) {
	if(cfg->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	php_error(E_NOTICE, "php_mod_java(%d): waiting for server another %d seconds",57, count);
	
	sleep(1);
  }
  return (cfg->cid && count)?SUCCESS:FAILURE;
}

/*
 return 0 if user has hard-coded the socketname
*/
static short can_fork() {
  return (java_ini_updated&U_SOCKNAME)==0;
}

/* handle keyboard interrupt */
static int s_pid=0;
static void s_kill(int sig) {
  if(s_pid) kill(s_pid, SIGTERM);
}

void java_start_server() {
  int pid=0, err=0, p[2];
  if(java_test_server() == FAILURE) {
	if(pipe(p)!=-1) {
	  if(can_fork()) {
		if(!(pid=fork())) {		/* daemon */
		  close(p[0]);
		  if(!fork()) {			/* guard */
			if(!(pid=fork())) {	/* java */
			  setsid();
			  close(p[1]);
			  exec_vm(); 
			  exit(105);
			}
			/* protect guard */
			signal(SIGHUP, SIG_IGN); 
			s_pid=pid; signal(SIGINT, s_kill); 
			signal(SIGTERM, SIG_IGN);
			
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
  }
  cfg->cid=pid;
  cfg->err=p[0];
  java_wait_server();
}


static void wait_for_daemon() {
  struct pollfd pollfd[1] = {{cfg->err, POLLIN, 0}};
  int err, c;

  assert(cfg->err);
  assert(cfg->cid);

  /* first kill is trapped, second kill is received with default
	 handler. If the server still exists, we send it a -9 */
  for(c=3; c>0 && cfg->cid; c--) {
	if (!(!cfg->err || (cfg->err && !(err=poll(pollfd, 1, 0))))) break;
	if(c>1) {
	  kill(cfg->cid, SIGTERM);
	  sleep(1);
	  if (!(!cfg->err || (cfg->err && !(err=poll(pollfd, 1, 0))))) break;
	  sleep(4);
	} else {
	  kill(cfg->cid, SIGKILL);
	}
  }
  if(cfg->err) {
	if((read(cfg->err, &err, sizeof err))!=sizeof err) err=0;
	//printf("VM terminated with code: %ld\n", err);
	close(cfg->err);
	cfg->err=0;
  }
}

void php_java_shutdown_library() 
{
  if(cfg->cid) wait_for_daemon();
}


#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
