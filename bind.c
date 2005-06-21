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
#include <time.h>

/* path and dir separators */
#include "php_wrapper.h"
#include "zend.h"
#include "ext/session/php_session.h"

#include "php_java.h"

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

const static char inet_socket_prefix[]="INET:";
const static char local_socket_prefix[]="LOCAL:";

#if EXTENSION == JAVA
static const char* const wrapper = EXTENSION_DIR/**/"/RunJavaBridge";
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS]) {
  static const char separator[2] = {ZEND_PATHS_SEPARATOR, 0};
  char *s, *p;
  char*program=EXT_GLOBAL(cfg)->vm;
  char*cp=EXT_GLOBAL(cfg)->classpath;
  char*lib_path=EXT_GLOBAL(cfg)->ld_library_path;
  char*sys_libpath=getenv("LD_LIBRARY_PATH");
  char*home=EXT_GLOBAL(cfg)->vm_home;
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(cfg)->sockname, *cfg_logFile=EXT_GLOBAL(cfg)->logFile;

  /* send a prefix so that the server does not select a different
   protocol */
  sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1);
  strcpy(sockname, s_prefix);
  strcat(sockname, cfg_sockname);
  
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
  args[6] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[7] = strdup(cfg_logFile);
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
#elif EXTENSION == MONO
static const char* const wrapper = EXTENSION_DIR/**/"/RunMonoBridge";
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS]) {
  char*program=EXT_GLOBAL(cfg)->vm;
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(cfg)->sockname, *cfg_logFile=EXT_GLOBAL(cfg)->logFile;

  args[0]=strdup(program);		/* mono */
  args[1] = strdup(EXTENSION_DIR/**/"/MonoBridge.exe");
  /* send a prefix so that the server does not select a different
   protocol */
/*   sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1); */
/*   strcpy(sockname, s_prefix); */
/*   strcat(sockname, cfg_sockname); */
/*   args[2] = sockname; */
  args[2] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[3] = strdup(cfg_logFile);
  args[4] = NULL;
  env[0] = NULL;
}
#endif
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
  
/*
 * Get a string of the server arguments. Useful for display only.
 */
char*EXT_GLOBAL(get_server_string)() {
  short must_use_wrapper = use_wrapper();
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  EXT_GLOBAL(get_server_args)(env, args);
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
  extern int EXT_GLOBAL(bridge_main)(int argc, char**argv) ;
  static char*env[N_SENV];
  static char*args[N_SARGS];
  EXT_GLOBAL(get_server_args)(env, args, 0);
  if(N_SENV>2) {
	putenv(env[0]);
	putenv(env[1]);
  }
  EXT_GLOBAL(bridge_main)(N_SARGS, args);
#else
  static char*env[N_SENV];
  static char*_args[N_SARGS+1];
  char **args=_args+1;
  EXT_GLOBAL(get_server_args)(env, args);
  if(N_SENV>2) {
	putenv(env[0]);
	putenv(env[1]);
  }
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
  n = connect(sock,(struct sockaddr*)&EXT_GLOBAL(cfg)->saddr, sizeof EXT_GLOBAL(cfg)->saddr);
  if(n==-1) { close(sock); return -1; }
  return sock;
}

/*
  return 0 if user has hard-coded the socketname
*/
static short can_fork() {
  return EXT_GLOBAL(cfg)->can_fork;
}

/*
 * Test for a running server.  Return the server name and the socket
 * if _socket!=NULL. If all ckecks fail a local backend is started.
 */
char* EXT_GLOBAL(test_server)(int *_socket, short *local) {
  int sock;
  short called_from_init = !(local && _socket);
  short socketname_set = (EXT_GLOBAL(ini_last_updated)&U_SOCKNAME);

  if(local) *local=0;
  /* check for local server if socketname set or (socketname not set
	 and hosts not set), in which case we may have started a local
	 backend ourselfs. Do not check if socketname not set and we are
	 called from init, in which case we know that a local backend is
	 not running. */
  if (((socketname_set || can_fork()) && (socketname_set || !called_from_init))
	  && -1!=(sock=test_local_server()) ) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	if(local) *local=1;
	return strdup(EXT_GLOBAL(cfg)->sockname);
  }

  /* host list */
  if(EXT_GLOBAL(cfg)->hosts && strlen(EXT_GLOBAL(cfg)->hosts)) {
	char *host, *hosts = strdup(EXT_GLOBAL(cfg)->hosts);
	
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
	  memset(&saddr, 0, sizeof saddr);
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
  struct pollfd pollfd[1] = {{EXT_GLOBAL(cfg)->err, POLLIN, 0}};
  int count=15, sock;
  
  /* wait for the server that has just started */
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server()) && --count) {
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d seconds",57, count);
	
	sleep(1);
  }
  close(sock);
  return (EXT_GLOBAL(cfg)->cid && count)?SUCCESS:FAILURE;
#else
  return SUCCESS;
#endif
}


/* handle keyboard interrupt */
static int s_pid=0;
static void s_kill(int sig) {
#ifndef __MINGW32__
  if(s_pid) kill(s_pid, SIGTERM);
#endif
}

void EXT_GLOBAL(start_server)() {
  int pid=0, err=0, p[2];
  char *test_server = 0;
#ifndef __MINGW32__
  if(can_fork() && !(test_server=EXT_GLOBAL(test_server)(0, 0)) && pipe(p)!=-1) {
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
	
	EXT_GLOBAL(cfg)->cid=pid;
	EXT_GLOBAL(cfg)->err=p[0];
	wait_server();
  } else
#endif /* MINGW32 */
	{
	  EXT_GLOBAL(cfg)->cid=EXT_GLOBAL(cfg)->err=0;
	  free(test_server);
	}
}

static void wait_for_daemon() {
#ifndef __MINGW32__
  struct pollfd pollfd[1] = {{EXT_GLOBAL(cfg)->err, POLLIN, 0}};
  int err, c;

  assert(EXT_GLOBAL(cfg)->err);
  assert(EXT_GLOBAL(cfg)->cid);

  /* first kill is trapped, second kill is received with default
	 handler. If the server still exists, we send it a -9 */
  for(c=3; c>0 && EXT_GLOBAL(cfg)->cid; c--) {
	if (!(!EXT_GLOBAL(cfg)->err || (EXT_GLOBAL(cfg)->err && !(err=poll(pollfd, 1, 0))))) break;
	if(c>1) {
	  kill(EXT_GLOBAL(cfg)->cid, SIGTERM);
	  sleep(1);
	  if (!(!EXT_GLOBAL(cfg)->err || (EXT_GLOBAL(cfg)->err && !(err=poll(pollfd, 1, 0))))) break;
	  sleep(4);
	} else {
	  kill(EXT_GLOBAL(cfg)->cid, SIGKILL);
	}
  }
  if(EXT_GLOBAL(cfg)->err) {
	if((read(EXT_GLOBAL(cfg)->err, &err, sizeof err))!=sizeof err) err=0;
	//printf("VM terminated with code: %ld\n", err);
	close(EXT_GLOBAL(cfg)->err);
	EXT_GLOBAL(cfg)->err=0;
  }
#endif
}

void EXT_GLOBAL(shutdown_library)() 
{
  if(EXT_GLOBAL(cfg)->cid) wait_for_daemon();
}


#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
