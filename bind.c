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
#include "multicast.h"

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
#ifdef CFG_JAVA_SOCKET_INET
  static char socket_prefix[]="INET:";
#else
  static char socket_prefix[]="LOCAL:";
#endif
  char *sockname;
  if(cfg->can_fork) {			/* send a prefix so that the server
								   does not select a different
								   protocol */
	sockname = malloc((sizeof socket_prefix)+strlen(cfg->sockname));
	strcpy(sockname, socket_prefix);
	strcat(sockname, cfg->sockname);
  } else {
	sockname=strdup(cfg->sockname);
  }
  
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
  args[4] = strdup("php.java.bridge.JavaBridge");

  args[5] = sockname;
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

#ifndef __MINGW32__
  if(!stat(wrapper, &buf) && (S_IFREG&buf.st_mode)) {
	if(getuid()==buf.st_uid)
	  use_wrapper=(S_IXUSR&buf.st_mode);
	else if(getgid()==buf.st_gid)
	  use_wrapper=(S_IXGRP&buf.st_mode);
	else 
	  use_wrapper=(S_IXOTH&buf.st_mode);
  }
#endif

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


static int test_local_server() {
  int sock, n;

#ifndef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock==-1) return -1;
  n = connect(sock,(struct sockaddr*)&cfg->saddr, sizeof cfg->saddr);
  if(n==-1) { close(sock); return -1; }
  return sock;
}

static int test_server(int port) {
  int sock, n;
  struct sockaddr_in saddr;

  sock = socket (PF_INET, SOCK_STREAM, 0);
  if(sock==-1) return -1;
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(port);
  saddr.sin_addr.s_addr=htonl(INADDR_ANY);  
  n = connect(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) { close(sock); return -1; }
  return sock;
}

char* java_test_server(int *_socket, unsigned char spec) {
  int sock, port;

  /* multicast */
  php_java_send_multicast(cfg->mc_socket, spec);
  port = php_java_recv_multicast(cfg->mc_socket, spec);

  if(-1!=port) {
	if(-1!=(sock=test_server(port))) {
	  if(_socket) {
		*_socket=sock;
	  } else {
		close(sock);
	  }
	  return strdup(GROUP_ADDR);
	}
  }

  /* local server */
  if(-1!=(sock=test_local_server())) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	return strdup(cfg->sockname);
  }

  /* host list */
  if(cfg->hosts && strlen(cfg->hosts)) {
	char *host, *hosts = strdup(cfg->hosts);
	
	assert(hosts); if(!hosts) return 0;
	for(host=strtok(hosts, ";"); host; host=strtok(0, ";")) {
	  struct sockaddr_in saddr;
	  char *_port = strrchr(host, ':'), *ret;
	  int port = 0;
	  
	  if(_port) { 
		*_port++=0;
		if(strlen(_port)) port=atoi(_port);
	  }
	  if(!port) port=atoi(DEFAULT_PORT);
	  saddr.sin_family = AF_INET;
	  saddr.sin_port=htons(port);
#ifndef __MINGW32__
	  if(!isdigit(*host)) {
		struct hostent *hostent = gethostbyname(host);
		if(hostent) {
		  memcpy(&saddr.sin_addr,hostent->h_addr,sizeof(struct in_addr));
		} else {
		  inet_aton(host, &saddr.sin_addr);
		}
	  } else {
		inet_aton(host, &saddr.sin_addr);
	  }
#else
	  saddr.sin_addr.s_addr = inet_addr(host);
#endif

	  sock = socket (PF_INET, SOCK_STREAM, 0);
	  if(-1==sock) continue;
	  if (-1==connect(sock,(struct sockaddr*)&saddr, sizeof (struct sockaddr))) {
		close(sock);
		continue;
	  }
	  if(_socket) *_socket=sock;
	  if(_port) _port[-1]=':';
	  ret = strdup(host);
	  free(hosts);
	  return ret;
	}
	free(hosts);
  }
  return 0;
}

static int wait_server() {
#ifndef __MINGW32__
  struct pollfd pollfd[1] = {{cfg->err, POLLIN, 0}};
  int count=15, sock;
  
  /* wait for the server that has just started */
  while(cfg->cid && -1==(sock=test_local_server()) && --count) {
	if(cfg->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	php_error(E_NOTICE, "php_mod_java(%d): waiting for server another %d seconds",57, count);
	
	sleep(1);
  }
  close(sock);
  return (cfg->cid && count)?SUCCESS:FAILURE;
#else
  return SUCCESS;
#endif
}

/*
 return 0 if user has hard-coded the socketname
*/
static short can_fork() {
  return cfg->can_fork;
}

/* handle keyboard interrupt */
static int s_pid=0;
static void s_kill(int sig) {
#ifndef __MINGW32__
  if(s_pid) kill(s_pid, SIGTERM);
#endif
}

void java_start_server() {
  int pid=0, err=0, p[2];
  char *test_server;
#ifndef __MINGW32__
  if(!(test_server=java_test_server(0, 0))) {
	if(can_fork()) {
	  if(pipe(p)!=-1) {
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
	cfg->cid=pid;
	cfg->err=p[0];
	wait_server();
  } else
#endif /* MINGW32 */
	{
	  cfg->cid=cfg->err=0;
	  free(test_server);
	}
}

static void wait_for_daemon() {
#ifndef __MINGW32__
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
#endif
}

void php_java_shutdown_library() 
{
  if(cfg->cid) wait_for_daemon();
}


#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
