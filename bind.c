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
#include <errno.h>
#include <time.h>

/* path and dir separators */
#include "php_wrapper.h"
#include "zend.h"
#include "ext/session/php_session.h"

#include "php_java.h"
#include "java_bridge.h"

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

const static char inet_socket_prefix[]="INET_LOCAL:";
const static char local_socket_prefix[]="LOCAL:";
const static char ext_dir[] = "extension_dir";

EXT_EXTERN_MODULE_GLOBALS(EXT)


#if EXTENSION == JAVA
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  static const char separator[2] = {ZEND_PATHS_SEPARATOR, 0};
  char *s, *p;
  char*program=EXT_GLOBAL(cfg)->vm;
  char*cp=EXT_GLOBAL(cfg)->classpath;
  char*lib_path=EXT_GLOBAL(cfg)->ld_library_path;
  char*sys_libpath=getenv("LD_LIBRARY_PATH");
  char*home=EXT_GLOBAL(cfg)->vm_home;
  char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;

  /* if socketname is off, show the user how to start a TCP backend */
  if(for_display && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	cfg_sockname="0";
	s_prefix=inet_socket_prefix;
	cfg_logFile="";
  }
  /* send a prefix so that the server does not select a different
   protocol */
  sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1);
  strcpy(sockname, s_prefix);
  strcat(sockname, cfg_sockname);

								/* library path usually points to the
								   extension dir */
  if(!(EXT_GLOBAL(option_set_by_user) (U_LIBRARY_PATH, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	if(ext) lib_path = ext;
  }
  
  if(!sys_libpath) sys_libpath="";
  args[0]=strdup(program);
  s="-Djava.library.path=";
  p=malloc(strlen(s)+strlen(lib_path)+1);
  strcpy(p, s); strcat(p, lib_path);
  args[1] = p;					/* library path */
  s="-Djava.class.path=";
								/* library path usually points to the
								   extension dir */
  if(ext && !(EXT_GLOBAL(option_set_by_user) (U_CLASSPATH, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	static char bridge[]="/JavaBridge.jar";
	char *slash;
	p=malloc(strlen(s)+strlen(ext)+sizeof(bridge));
	strcpy(p, s); strcat(p, ext); 
	slash=p+strlen(p)-1;
	if(*p&&*slash==ZEND_PATHS_SEPARATOR) *slash=0;
	strcat(p, bridge);
  } else {
	p=malloc(strlen(s)+strlen(cp)+1);
	strcpy(p, s); strcat(p, cp);
  }
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
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  static const char executable[] = "/MonoBridge.exe";
  char *p, *slash;
  char*program=EXT_GLOBAL(cfg)->vm;
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;
  char*home = EXT_GLOBAL(cfg)->vm_home;
  if(!(EXT_GLOBAL(option_set_by_user) (U_JAVA_HOME, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
	if(ext) home = ext;
  }
  args[0]=strdup(program);		/* mono */
  p=malloc(strlen(home)+sizeof executable);
  strcpy(p, home); 
  slash=p+strlen(p)-1;
  if(*p&&*slash==ZEND_PATHS_SEPARATOR) *slash=0;
  strcat(p, executable);

  args[1] = p;
  /* if socketname is off, show the user how to start a TCP backend */
  if(for_display && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	static const char zero[] = "0";
	cfg_sockname="0";
	s_prefix=inet_socket_prefix;
	cfg_logFile="";
  }
  /* send a prefix so that the server does not select a different */
  /* protocol */
  sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1);
  strcpy(sockname, s_prefix);
  strcat(sockname, cfg_sockname);
  args[2] = sockname;
  args[3] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[4] = strdup(cfg_logFile);
  args[5] = NULL;
  env[0] = NULL;
}
#endif
static short use_wrapper(char*wrapper) {
  struct stat buf;
  short use_wrapper=(EXT_GLOBAL(option_set_by_user) (U_WRAPPER, EXT_GLOBAL(ini_user)));
  if(use_wrapper) return use_wrapper;

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
static char*get_server_string(short for_display TSRMLS_DC) {
#ifndef __MINGW32__
  static const char quote[] = "'";
#else
  static const char quote[] = "\"";
#endif
  short must_use_wrapper = use_wrapper(EXT_GLOBAL(cfg)->wrapper);
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  EXT_GLOBAL(get_server_args)(env, args, for_display TSRMLS_CC);
  if(must_use_wrapper)
	length+=strlen(EXT_GLOBAL(cfg)->wrapper)+1;
#ifndef __MINGW32__
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
#endif
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	size_t l;
	if(!args[i]) break;
	l=strlen(args[i]);
	length+=(l?l:2)+1;
  }
  s=malloc(length+1);
  assert(s); if(!s) exit(9);

  *s=0;
#ifndef __MINGW32__
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	strcat(s, env[i]); strcat(s, " ");
	free(env[i]);
  }
#endif
  if(must_use_wrapper) {
	strcat(s, EXT_GLOBAL(cfg)->wrapper);
	strcat(s, " ");
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	if(!strlen(args[i])) strcat(s,quote);
	strcat(s, args[i]);
	if(!strlen(args[i])) strcat(s,quote);
	strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}
char*EXT_GLOBAL(get_server_string)(TSRMLS_D) {
  return get_server_string(1 TSRMLS_CC);
}

static void exec_vm(TSRMLS_D) {
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
  EXT_GLOBAL(get_server_args)(env, args, 0 TSRMLS_CC);
  if(N_SENV>2) {
	putenv(env[0]);
	putenv(env[1]);
  }
  if(use_wrapper(EXT_GLOBAL(cfg)->wrapper)) *--args = strdup(EXT_GLOBAL(cfg)->wrapper);
  execv(args[0], args);
#endif
}


static int test_local_server(void) {
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
static short can_fork(void) {
  return EXT_GLOBAL(cfg)->can_fork;
}

/*
 * Test for a running server.  Return the server name and the socket
 * if _socket!=NULL. If all ckecks fail a local backend is started.
 */
char* EXT_GLOBAL(test_server)(int *_socket, short *local, struct sockaddr*_saddr TSRMLS_DC) {
  int sock;
  short called_from_init = !(local || _socket);

								/* java.servlet=On forces
								   java.socketname Off */
  short socketname_set = 
	(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, JG(ini_user))) && 
	!(EXT_GLOBAL(option_set_by_user) (U_SERVLET, JG(ini_user)));

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
	return strdup(EXT_GLOBAL(get_sockname)(TSRMLS_C));
  }

  /* host list */
  if(JG(hosts) && strlen(JG(hosts))) {
	char *host, *hosts = strdup(JG(hosts));
	
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
	  else close(sock);
	  if(_port) _port[-1]=':';
	  ret = strdup(host);
	  free(hosts);
	  if(_saddr) memcpy(_saddr, &saddr, sizeof (struct sockaddr));
	  return ret;
	}
	free(hosts);
  }
  return 0;
}

static int wait_server(void) {
  int count=15, sock;
#ifndef __MINGW32__
  struct pollfd pollfd[1] = {{EXT_GLOBAL(cfg)->err, POLLIN, 0}};
  
  /* wait for the server that has just started */
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server()) && --count) {
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	if(count<=10) php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d seconds",57, count);
	
	sleep(1);
  }
#else

  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server()) && --count) {
	if(count<=10) php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d seconds",57, count);
	Sleep(1000);
  }
#endif
  close(sock);
  return (EXT_GLOBAL(cfg)->cid && count)?SUCCESS:FAILURE;
}


/* handle keyboard interrupt */
#ifndef __MINGW32__
static int s_pid=0;
static void s_kill(int sig) {
  if(s_pid) kill(s_pid, SIGTERM);
}
#else
static PROCESS_INFORMATION s_pid;
static void s_kill(int sig) {
  if(s_pid.hProcess) TerminateProcess(s_pid.hProcess, 1);
}
#endif

void EXT_GLOBAL(start_server)(TSRMLS_D) {
  int pid=0, err=0, p[2];
  char *test_server = 0;
#ifndef __MINGW32__
  if(can_fork() && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC)) && pipe(p)!=-1) {
	if(!(pid=fork())) {		/* daemon */
	  close(p[0]);
	  if(!fork()) {			/* guard */
		setsid();
		if(!(pid=fork())) {	/* java */
		  close(p[1]);
		  exec_vm(TSRMLS_C); 
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
#else
	if(can_fork() && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC))) {
	  char *cmd = get_server_string(0 TSRMLS_CC);
	  DWORD properties = CREATE_NEW_CONSOLE;
	  STARTUPINFO su_info;
	  PROCESS_INFORMATION p_info;

	  ZeroMemory(&su_info, sizeof(STARTUPINFO));
	  su_info.cb = sizeof(STARTUPINFO);
	  EXT_GLOBAL(cfg)->cid=0;
	  if(CreateProcess( NULL, cmd, NULL, NULL, 1, properties, NULL, NULL, &su_info, &s_pid)) {
		EXT_GLOBAL(cfg)->cid=s_pid.dwProcessId;
	  }
	  wait_server();
	} else
#endif /* MINGW32 */
	  {
		EXT_GLOBAL(cfg)->cid=EXT_GLOBAL(cfg)->err=0;
	  }
  if(test_server) free(test_server);
}

static void wait_for_daemon(void) {
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
#else
  s_kill(0);
  Sleep(1000);
#endif
}

void EXT_GLOBAL(shutdown_library)() 
{
  if(EXT_GLOBAL(cfg)->cid) wait_for_daemon();
}

void EXT_GLOBAL(sys_error)(const char *str, int code) {
#ifndef __MINGW32__
  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: %s. %s.", code, strerror(errno), str);
#else
  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d) system error code: %ld. %s.", code, (long)GetLastError(), str);
#endif
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
