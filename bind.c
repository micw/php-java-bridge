/*-*- mode: C; tab-width:4 -*-*/

/* bind.c -- create and connect to the PHP/Java Bridge back end.

  Copyright (C) 2003-2007 Jost Boekemeier

  This file is part of the PHP/Java Bridge.

  The PHP/Java Bridge ("the library") is free software; you can
  redistribute it and/or modify it under the terms of the GNU General
  Public License as published by the Free Software Foundation; either
  version 2, or (at your option) any later version.

  The library is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with the PHP/Java Bridge; see the file COPYING.  If not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
  02111-1307 USA.

  Linking this file statically or dynamically with other modules is
  making a combined work based on this library.  Thus, the terms and
  conditions of the GNU General Public License cover the whole
  combination.

  As a special exception, the copyright holders of this library give you
  permission to link this library with independent modules to produce an
  executable, regardless of the license terms of these independent
  modules, and to copy and distribute the resulting executable under
  terms of your choice, provided that you also meet, for each linked
  independent module, the terms and conditions of the license of that
  module.  An independent module is a module which is not derived from
  or based on this library.  If you modify this library, you may extend
  this exception to your version of the library, but you are not
  obligated to do so.  If you do not wish to do so, delete this
  exception statement from your version. */

#include "php_java.h"

/* execve,select */
#ifndef __MINGW32__
#ifdef HAVE_SYS_TIME_H
#include <sys/time.h>
#endif
#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#endif
#endif

/* stat, mkdir */
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

/* opendir */
#ifndef __MINGW32__
#include <dirent.h>
#endif

/* fcntl */
#include <fcntl.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* signal */
#include <signal.h>

/* poll */
#ifdef HAVE_SYS_POLL_H
#include <sys/poll.h>
#endif

/* wait */
#include <sys/wait.h>

/* miscellaneous */
#include <stdio.h>
#include <errno.h>
#include <time.h>

/* path separator */
#include "php_wrapper.h"
#include "zend.h"

#include "java_bridge.h"
#include "init_cfg.h"

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

const static char servlet_socket_prefix[]="SERVLET_LOCAL:";
const static char inet_socket_prefix[]="INET_LOCAL:";
const static char local_socket_prefix[]="LOCAL:";
const static char ext_dir[] = "extension_dir";

EXT_EXTERN_MODULE_GLOBALS(EXT)

static short use_wrapper(char*wrapper) {
  struct stat buf;
  short use_wrapper=(EXT_GLOBAL(option_set_by_user) (U_WRAPPER, EXT_GLOBAL(ini_user)));
  if(use_wrapper) return use_wrapper;
#ifndef __MINGW32__
  /* do not change privileges when we are not running as root.
	 The RunJavaBridge and RunMonoBridge contains a similar test */
  if(getuid()) return 0;
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
/**
 * Check if the file ext_dir/javabridge.policy exists
 * and return an allocated string containing s+ext_dir/javabridge.policy
 * or NULL.
 * @param s The prefix, for example "java.security.policy="
 */
static char *check_policy(char *s) {
  struct stat buf;
  short use_policy = 0;
  char *p = 0;
#ifndef __MINGW32__
  const static char bridge[]="/javabridge.policy";
  char *slash, *ext= php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
  size_t slen = strlen(s);
  p=malloc(slen+strlen(ext)+sizeof(bridge)); if(!p) return 0;
  strcpy(p, s); strcat(p, ext);
  slash=p+strlen(p)-1; 	if(*p&&(*slash=='/'||*slash=='\\')) *slash=0;
  strcat(p, bridge);
  /* check if it exists */
  if(!stat(p+slen, &buf) && (S_IFREG&buf.st_mode)) {
	if(getuid()==buf.st_uid)
	  use_policy=(S_IRUSR&buf.st_mode);
	else if(getgid()==buf.st_gid)
	  use_policy=(S_IRGRP&buf.st_mode);
	else 
	  use_policy=(S_IROTH&buf.st_mode);
  }
  if(!use_policy) { free(p); p=0; }
#endif
  return p;
}

/* Windows can handle slashes as well as backslashes so use / everywhere */
static const char path_separator[2] = {ZEND_PATHS_SEPARATOR, 0};
static const char bridge_base[] = "-Dphp.java.bridge.base=";
#if EXTENSION == JAVA
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  char *s, *p;
  char*program=EXT_GLOBAL(cfg)->vm;
  char*cp=EXT_GLOBAL(cfg)->classpath;
  char*lib_path=EXT_GLOBAL(cfg)->ld_library_path;
  char*sys_libpath=getenv("LD_LIBRARY_PATH");
  char*home=EXT_GLOBAL(cfg)->vm_home;
  char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
  const char* s_prefix = inet_socket_prefix;
  short any_port = 1;			/* let back-end select the port# */
  if(!JG(java_socket_inet)) {
	s_prefix = local_socket_prefix;
	any_port = 0; //for_display
  }
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;

  /* if socketname is off, show the user how to start a TCP backend */
  if(any_port && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	if(EXT_GLOBAL(option_set_by_user)(U_SERVLET, JG(ini_user))) {
	  static const char default_port[] = "8080";
	  char *s = JG(hosts);
	  char *p = s ? strpbrk(s, "; ") : 0; if(p) *p=0;
	  p = s ? strchr(s, ':') : 0;
	  if(p) p++;
	  cfg_sockname = p ? p : (char*)default_port;
	  s_prefix=servlet_socket_prefix;
	} else {
	  cfg_sockname="0";
	  s_prefix=inet_socket_prefix;
	  //cfg_logFile="";
	}
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
  if(!*program) {				/* look into extension_dir then */
	static const char java[] = "/java";
	if(ext) {
	  program = malloc(strlen(ext)+sizeof(java)); assert(program); if(!program) exit(6);
	  strcpy(program, ext); strcat(program, java);
	} else {
	  program = strdup(program);
	}
  } else {
	program = strdup(program);
  }
  
  if(!sys_libpath) sys_libpath="";
  args[0]=program;
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
	if(*p&&(*slash=='/'||*slash=='\\')) *slash=0;
	strcat(p, bridge);
  } else {
	p=malloc(strlen(s)+strlen(cp)+1);
	strcpy(p, s); strcat(p, cp);
  }
  args[2] = p;					/* user classes */

  /* policy */
  s="-Djava.security.policy="; p=0;
  if(!p && EXT_GLOBAL(option_set_by_user) (U_POLICY, EXT_GLOBAL(ini_user))) {
	char *cp = EXT_GLOBAL(cfg)->policy;
	if(*cp==0||cp[1]==0) {		/* policy=On (stored as '\0' or '1\0') */
	  p = check_policy(s);
	} else {
	  p=malloc(strlen(s)+strlen(cp)+1);
	  strcpy(p, s); strcat(p, cp);
	}
  }
  if(!p) {					/* no policy at all */
	p = strdup("-Djava.awt.headless=true");
  }
  args[3] = p;

								/* base */
  p = malloc(strlen(ext)+sizeof(bridge_base));
  strcpy(p, bridge_base); strcat(p, ext);
  args[4] = p;

  args[5] = strdup("php.java.bridge.Standalone");
  args[6] = sockname;
  args[7] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[8] = strdup(cfg_logFile);
  args[9] = NULL;

  if(*home) {					/* set VM home */
	s="JAVA_HOME=";
	p=malloc(strlen(s)+strlen(home)+1);
	strcpy(p, s); strcat(p, home);
	env[0] = p;
  } else {						/* VM in PATH; don't set java home */
	env[0] = strdup("");
  }

  s="LD_LIBRARY_PATH=";
  p=malloc(strlen(s)+strlen(lib_path)+1+strlen(sys_libpath)+1);
  strcpy(p, s); strcat(p, lib_path); 
  strcat(p, path_separator); strcat(p, sys_libpath);
  env[1] = p;					/* library path */
  env[2] = NULL;
}
#elif EXTENSION == MONO
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  static const char executable[] = "/MonoBridge.exe";
  char *p, *slash;
  char*program=EXT_GLOBAL(cfg)->vm;
  const char* s_prefix = inet_socket_prefix;
  short any_port = 1;			/* let back-end select the port# */
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;
  char*home = EXT_GLOBAL(cfg)->vm_home;
  if(!JG(java_socket_inet)) {
	s_prefix = local_socket_prefix;
	any_port = 0; //for_display
  }
  if(!(EXT_GLOBAL(option_set_by_user) (U_JAVA_HOME, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
	if(ext) home = ext;
  }
  args[0]=strdup(program);		/* mono */
  p=malloc(strlen(home)+sizeof executable);
  strcpy(p, home); 
  slash=p+strlen(p)-1;
  if(*p&&(*slash=='/'||*slash=='\\')) *slash=0;
  strcat(p, executable);

  args[1] = p;
  /* if socketname is off, show the user how to start a TCP backend */
  if(any_port && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	cfg_sockname="0";
	s_prefix=inet_socket_prefix;
	//cfg_logFile="";
  }
  /* send a prefix so that the server does not select a different */
  /* channel */
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
  
/*
 * Get a string of the server arguments. Useful for display only.
 */
static char*get_server_string(short for_display TSRMLS_DC) {
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
	strcat(s, args[i]);
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
  int i, n;
  static char*env[N_SENV];
  static char*_args[N_SARGS+1];
  char **args=_args+1, *cmd;
  EXT_GLOBAL(get_server_args)(env, args, 0 TSRMLS_CC);
  if(N_SENV>2) {
	if(*env[0]) putenv(env[0]);
	if(*env[1]) putenv(env[1]);
  }
  for(i=3, n=dup(1); i<=n; i++) close(i);
  if(use_wrapper(EXT_GLOBAL(cfg)->wrapper)) {
	*--args = strdup(EXT_GLOBAL(cfg)->wrapper);
	execv(args[0], args);
  }
  if(*args[0]=='/') execv(args[0], args); else execvp(args[0], args);

#if EXTENSION == JAVA
  execvp("java", args);
#elif EXTENSION == MONO
  execvp("mono", args);
#endif

  /* exec failed */
  cmd = get_server_string(0 TSRMLS_CC);
  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: Could not execute backend: %s: %s", 105, cmd, strerror(errno));
  free(cmd);
}

static const int is_true = 1;
static int test_local_server(TSRMLS_D) {
  int sock, n;
  if(!JG(java_socket_inet)) {
#ifndef CFG_JAVA_SOCKET_INET
	sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#endif
  } else {
	sock = socket (PF_INET, SOCK_STREAM, 0);
	if(sock!=-1) setsockopt(sock, 0x6, TCP_NODELAY, (void*)&is_true, sizeof is_true);
  }
  if(sock==-1) return -1;
  if(JG(java_socket_inet)) {
	n = connect(sock,(struct sockaddr*)&EXT_GLOBAL(cfg)->saddr.in, sizeof EXT_GLOBAL(cfg)->saddr.in);
  } else {
#ifndef CFG_JAVA_SOCKET_INET
	n = connect(sock,(struct sockaddr*)&EXT_GLOBAL(cfg)->saddr.un, sizeof EXT_GLOBAL(cfg)->saddr.un);
#endif
  }
  if(n==-1) { close(sock); return -1; }
  return sock;
}

/*
 * return 0 if user has hard-coded the socketname or if
 * X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT is set at run-time.
 */
static short can_fork(TSRMLS_D) {
  return EXT_GLOBAL(cfg)->can_fork && !(EXT_GLOBAL(option_set_by_user) (U_SERVLET, JG(ini_user)));
}

short EXT_GLOBAL(can_fork)(TSRMLS_D) {
  return can_fork(TSRMLS_C);
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
  short socketname_set = EXT_GLOBAL(cfg)->socketname_set &&
	EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, JG(ini_user)) &&
	!(EXT_GLOBAL(option_set_by_user) (U_SERVLET, JG(ini_user)));

  if(local) *local=0;
  /* check for local server if socketname set or (socketname not set
	 and hosts not set), in which case we may have started a local
	 backend ourselfs. Do not check if socketname not set and we are
	 called from init, in which case we know that a local backend is
	 not running. */
  if (((socketname_set || can_fork(TSRMLS_C)) && (socketname_set || !called_from_init))
	  && -1!=(sock=test_local_server(TSRMLS_C)) ) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	if(local) *local=1;
	return strdup(EXT_GLOBAL(get_sockname)(TSRMLS_C));
  }

  /* host list */
  if(JG(hosts) && *(JG(hosts))) {
	char *host, *hosts = strdup(JG(hosts));
	
	assert(hosts); if(!hosts) return 0;
	for(host=strtok(hosts, "; "); host; host=strtok(0, "; ")) {
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
	  if(_socket) {
		*_socket=sock;
		setsockopt(sock, 0x6, TCP_NODELAY, (void*)&is_true, sizeof is_true);
	  }
	  else close(sock);
	  if(_port) _port[-1]=':';
	  ret = strdup(host);
	  free(hosts);
	  if(_saddr) memcpy(_saddr, &saddr, sizeof (struct sockaddr));
	  if(EXT_GLOBAL(cfg)->socketname_set)
		EXT_GLOBAL(cfg)->socketname_set = 0;
	  return ret;
	}
	free(hosts);
  }

  socketname_set = EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, JG(ini_user)) ;
  if (((socketname_set || can_fork(TSRMLS_C)) && (socketname_set || !called_from_init))
	  && -1!=(sock=test_local_server(TSRMLS_C)) ) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	if(local) *local=1;
	if(!EXT_GLOBAL(cfg)->socketname_set)
	  EXT_GLOBAL(cfg)->socketname_set = 1;
	return strdup(EXT_GLOBAL(get_sockname)(TSRMLS_C));
  }

  return 0;
}
static const long timeout = 50000l; /* ys */
static void sleep_ms() {
  struct timeval timeval = {0l, timeout};
  select(0, 0, 0, 0, &timeval);
}
static int wait_server(TSRMLS_D) {
#ifndef __MINGW32__ 
  static const int wait_count = 30;
  int count=wait_count, sock;

#ifdef HAVE_POLL /* some ancient OS (Darwin, OSX) don't have poll */
  struct pollfd pollfd[1] = {{EXT_GLOBAL(cfg)->err, POLLIN, 0}};
#endif
  
  /* wait for the server that has just started */
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server(TSRMLS_C)) && --count) {
#ifdef HAVE_POLL
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
#endif
	sleep_ms();
  }
  count=30;
  while(EXT_GLOBAL(cfg)->cid && -1==sock && -1==(sock=test_local_server(TSRMLS_C)) && --count) {
#ifdef HAVE_POLL
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
#endif
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d seconds",57, count);
	sleep(1);
  }
#else
  static const int wait_count = 5;
  int count=wait_count, sock;
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server(TSRMLS_C)) && --count) {
	Sleep(500);
  }
  count=15;
  while(EXT_GLOBAL(cfg)->cid && -1==sock && -1==(sock=test_local_server(TSRMLS_C)) && --count) {
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d interval",57, count);
	Sleep(500);
  }
#endif
  if(EXT_GLOBAL(cfg)->cid && count) {
	close(sock);
	return SUCCESS;
  } else {
	return FAILURE;
  }
}


/* handle keyboard interrupt */
#ifndef __MINGW32__
static int s_pid=0;
static void s_kill(int sig) {
  if(s_pid) kill(s_pid, SIGTERM);
}
#else
#ifndef _WIN32_WINNT
# define _WIN32_WINNT 0x500
#endif

#include <windows.h>
#include <tchar.h>
#include <stdarg.h>
#include <tlhelp32.h>

static struct s_pid {
  short use_wrapper;
  PROCESS_INFORMATION p;
} s_pid;



/**
 * Unix kill emulation for windows.
 * From http://www.rsdn.ru/?qna/?baseserv/killproc.xml
 */
static BOOL WINAPI KillProcess(IN DWORD dwProcessId)
{
  HANDLE hProcess;
  DWORD dwError;

  // first try to obtain handle to the process without the use of any
  // additional privileges
  hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, dwProcessId);
  if (hProcess == NULL)
    {
      if (GetLastError() != ERROR_ACCESS_DENIED)
		return FALSE;

      OSVERSIONINFO osvi;

      // determine operating system version
      osvi.dwOSVersionInfoSize = sizeof(osvi);
      GetVersionEx(&osvi);

      // we cannot do anything else if this is not Windows NT
      if (osvi.dwPlatformId != VER_PLATFORM_WIN32_NT)
		return FALSE;

      // enable SE_DEBUG_NAME privilege and try again

      TOKEN_PRIVILEGES Priv, PrivOld;
      DWORD cbPriv = sizeof(PrivOld);
      HANDLE hToken;

      // obtain the token of the current thread 
      if (!OpenThreadToken(GetCurrentThread(), 
						   TOKEN_QUERY|TOKEN_ADJUST_PRIVILEGES,
						   FALSE, &hToken))
		{
		  if (GetLastError() != ERROR_NO_TOKEN)
			return FALSE;

		  // revert to the process token
		  if (!OpenProcessToken(GetCurrentProcess(),
								TOKEN_QUERY|TOKEN_ADJUST_PRIVILEGES,
								&hToken))
			return FALSE;
		}

      assert(ANYSIZE_ARRAY > 0);

      Priv.PrivilegeCount = 1;
      Priv.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
      LookupPrivilegeValue(NULL, SE_DEBUG_NAME, &Priv.Privileges[0].Luid);

      // try to enable the privilege
      if (!AdjustTokenPrivileges(hToken, FALSE, &Priv, sizeof(Priv),
								 &PrivOld, &cbPriv))
		{
		  dwError = GetLastError();
		  CloseHandle(hToken);
		  return FALSE;
		}

      if (GetLastError() == ERROR_NOT_ALL_ASSIGNED)
		{
		  // the SE_DEBUG_NAME privilege is not present in the caller's
		  // token
		  CloseHandle(hToken);
		  return FALSE;
		}

      // try to open process handle again
      hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, dwProcessId);
      dwError = GetLastError();
		
      // restore the original state of the privilege
      AdjustTokenPrivileges(hToken, FALSE, &PrivOld, sizeof(PrivOld),
							NULL, NULL);
      CloseHandle(hToken);

      if (hProcess == NULL)
		return FALSE;
    }

  // terminate the process
  if (!TerminateProcess(hProcess, (UINT)-1))
    {
      dwError = GetLastError();
      CloseHandle(hProcess);
      return FALSE;
    }

  CloseHandle(hProcess);

  // completed successfully
  return TRUE;
}

typedef LONG	NTSTATUS;
typedef LONG	KPRIORITY;

#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)

#define STATUS_INFO_LENGTH_MISMATCH      ((NTSTATUS)0xC0000004L)

#define SystemProcessesAndThreadsInformation	5

typedef struct _CLIENT_ID {
  DWORD	    UniqueProcess;
  DWORD	    UniqueThread;
} CLIENT_ID;

typedef struct _UNICODE_STRING {
  USHORT	    Length;
  USHORT	    MaximumLength;
  PWSTR	    Buffer;
} UNICODE_STRING;

typedef struct _VM_COUNTERS {
  SIZE_T	    PeakVirtualSize;
  SIZE_T	    VirtualSize;
  ULONG	    PageFaultCount;
  SIZE_T	    PeakWorkingSetSize;
  SIZE_T	    WorkingSetSize;
  SIZE_T	    QuotaPeakPagedPoolUsage;
  SIZE_T	    QuotaPagedPoolUsage;
  SIZE_T	    QuotaPeakNonPagedPoolUsage;
  SIZE_T	    QuotaNonPagedPoolUsage;
  SIZE_T	    PagefileUsage;
  SIZE_T	    PeakPagefileUsage;
} VM_COUNTERS;

typedef struct _SYSTEM_THREADS {
  LARGE_INTEGER   KernelTime;
  LARGE_INTEGER   UserTime;
  LARGE_INTEGER   CreateTime;
  ULONG			WaitTime;
  PVOID			StartAddress;
  CLIENT_ID	    ClientId;
  KPRIORITY	    Priority;
  KPRIORITY	    BasePriority;
  ULONG			ContextSwitchCount;
  LONG			State;
  LONG			WaitReason;
} SYSTEM_THREADS, * PSYSTEM_THREADS;

// Note that the size of the SYSTEM_PROCESSES structure is different on
// NT 4 and Win2K, but we don't care about it, since we don't access neither
// IoCounters member nor Threads array

typedef struct _SYSTEM_PROCESSES {
  ULONG			NextEntryDelta;
  ULONG			ThreadCount;
  ULONG			Reserved1[6];
  LARGE_INTEGER   CreateTime;
  LARGE_INTEGER   UserTime;
  LARGE_INTEGER   KernelTime;
  UNICODE_STRING  ProcessName;
  KPRIORITY	    BasePriority;
  ULONG			ProcessId;
  ULONG			InheritedFromProcessId;
  ULONG			HandleCount;
  ULONG			Reserved2[2];
  VM_COUNTERS	    VmCounters;
#if _WIN32_WINNT >= 0x500
  IO_COUNTERS	    IoCounters;
#endif
  SYSTEM_THREADS  Threads[1];
} SYSTEM_PROCESSES, * PSYSTEM_PROCESSES;

static BOOL WINAPI KillProcessTreeNtHelper(IN PSYSTEM_PROCESSES pInfo, IN DWORD dwProcessId)
{
  assert(pInfo != NULL);

  PSYSTEM_PROCESSES p = pInfo;

  // kill all children first
  for (;;)
    {
      if (p->InheritedFromProcessId == dwProcessId)
		KillProcessTreeNtHelper(pInfo, p->ProcessId);

      if (p->NextEntryDelta == 0)
		break;

      // find the address of the next process structure
      p = (PSYSTEM_PROCESSES)(((LPBYTE)p) + p->NextEntryDelta);
    }

  // kill the process itself
  if (!KillProcess(dwProcessId))
    return GetLastError();

  return ERROR_SUCCESS;
}

static BOOL WINAPI KillProcessTreeWinHelper(IN DWORD dwProcessId)
{
  HINSTANCE hKernel;
  HANDLE (WINAPI * _CreateToolhelp32Snapshot)(DWORD, DWORD);
  BOOL (WINAPI * _Process32First)(HANDLE, PROCESSENTRY32 *);
  BOOL (WINAPI * _Process32Next)(HANDLE, PROCESSENTRY32 *);

  // get handle to KERNEL32.DLL
  hKernel = GetModuleHandle(_T("kernel32.dll"));
  assert(hKernel != NULL);

  // locate necessary functions in KERNEL32.DLL
  *(FARPROC *)&_CreateToolhelp32Snapshot =
    GetProcAddress(hKernel, "CreateToolhelp32Snapshot");
  *(FARPROC *)&_Process32First =
    GetProcAddress(hKernel, "Process32First");
  *(FARPROC *)&_Process32Next =
    GetProcAddress(hKernel, "Process32Next");

  if (_CreateToolhelp32Snapshot == NULL ||
      _Process32First == NULL ||
      _Process32Next == NULL)
    return ERROR_PROC_NOT_FOUND;

  HANDLE hSnapshot;
  PROCESSENTRY32 Entry;

  // create a snapshot
  hSnapshot = _CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnapshot == INVALID_HANDLE_VALUE)
    return GetLastError();

  Entry.dwSize = sizeof(Entry);
  if (!_Process32First(hSnapshot, &Entry))
    {
      DWORD dwError = GetLastError();
      CloseHandle(hSnapshot);
      return dwError;
    }

  // kill all children first
  do
    {
      if (Entry.th32ParentProcessID == dwProcessId)
		KillProcessTreeWinHelper(Entry.th32ProcessID);

      Entry.dwSize = sizeof(Entry);
    }
  while (_Process32Next(hSnapshot, &Entry));

  CloseHandle(hSnapshot);

  // kill the process itself
  if (!KillProcess(dwProcessId))
    return GetLastError();

  return ERROR_SUCCESS;
}

static BOOL WINAPI KillProcessEx(IN DWORD dwProcessId, IN BOOL bTree)
{
  if (!bTree)
    return KillProcess(dwProcessId);

  OSVERSIONINFO osvi;
  DWORD dwError;

  // determine operating system version
  osvi.dwOSVersionInfoSize = sizeof(osvi);
  GetVersionEx(&osvi);

  if (osvi.dwPlatformId == VER_PLATFORM_WIN32_NT &&
      osvi.dwMajorVersion < 5)
    {
      HINSTANCE hNtDll;
      NTSTATUS (WINAPI * _ZwQuerySystemInformation)(UINT, PVOID, ULONG, PULONG);

      // get handle to NTDLL.DLL
      hNtDll = GetModuleHandle(_T("ntdll.dll"));
      assert(hNtDll != NULL);

      // find the address of ZwQuerySystemInformation
      *(FARPROC *)&_ZwQuerySystemInformation =
		GetProcAddress(hNtDll, "ZwQuerySystemInformation");
      if (_ZwQuerySystemInformation == NULL)
		return FALSE;

      // obtain a handle to the default process heap
      HANDLE hHeap = GetProcessHeap();
    
      NTSTATUS Status;
      ULONG cbBuffer = 0x8000;
      PVOID pBuffer = NULL;

      // it is difficult to say a priory which size of the buffer 
      // will be enough to retrieve all information, so we start
      // with 32K buffer and increase its size until we get the
      // information successfully
      do
		{
		  pBuffer = HeapAlloc(hHeap, 0, cbBuffer);
		  if (pBuffer == NULL)
			return FALSE;

		  Status = _ZwQuerySystemInformation(
											 SystemProcessesAndThreadsInformation,
											 pBuffer, cbBuffer, NULL);

		  if (Status == STATUS_INFO_LENGTH_MISMATCH)
			{
			  HeapFree(hHeap, 0, pBuffer);
			  cbBuffer *= 2;
			}
		  else if (!NT_SUCCESS(Status))
			{
			  HeapFree(hHeap, 0, pBuffer);
			  return FALSE;
			}
		}
      while (Status == STATUS_INFO_LENGTH_MISMATCH);

      // call the helper function
      dwError = KillProcessTreeNtHelper((PSYSTEM_PROCESSES)pBuffer, 
										dwProcessId);
		
      HeapFree(hHeap, 0, pBuffer);
    }
  else
    {
      // call the helper function
      dwError = KillProcessTreeWinHelper(dwProcessId);
    }

  return dwError == ERROR_SUCCESS;
}

static void s_kill(int sig) {
  if(!s_pid.use_wrapper) {
								/* we can kill our child directly */
	if(s_pid.p.hProcess) TerminateProcess(s_pid.p.hProcess, 1);
  }
  else {
								/* emulate unix kill behaviour */
	if(s_pid.p.dwProcessId) KillProcessEx(s_pid.p.dwProcessId, 1);
  }
}



#endif
static void make_local_socket_info(short java_socket_inet TSRMLS_DC) {
  if(!java_socket_inet) {
#ifndef CFG_JAVA_SOCKET_INET
	memset(&EXT_GLOBAL(cfg)->saddr.un, 0, sizeof EXT_GLOBAL(cfg)->saddr.un);
	EXT_GLOBAL(cfg)->saddr.un.sun_family = AF_LOCAL;
	memset(EXT_GLOBAL(cfg)->saddr.un.sun_path, 0, sizeof EXT_GLOBAL(cfg)->saddr.un.sun_path);
	strcpy(EXT_GLOBAL(cfg)->saddr.un.sun_path, EXT_GLOBAL(get_sockname)(TSRMLS_C));
#ifdef HAVE_ABSTRACT_NAMESPACE
	*EXT_GLOBAL(cfg)->saddr.un.sun_path=0;
#endif
	assert(EXT_GLOBAL(cfg)->java_socket_inet == 0);
	EXT_GLOBAL(cfg)->java_socket_inet = 0;
#endif
  } else {
	memset(&EXT_GLOBAL(cfg)->saddr.in, 0, sizeof EXT_GLOBAL(cfg)->saddr.in);
	EXT_GLOBAL(cfg)->java_socket_inet = 1;
	EXT_GLOBAL(cfg)->saddr.in.sin_family = AF_INET;
	EXT_GLOBAL(cfg)->saddr.in.sin_port=htons(atoi(EXT_GLOBAL(get_sockname)(TSRMLS_C)));
	EXT_GLOBAL(cfg)->saddr.in.sin_addr.s_addr = inet_addr( "127.0.0.1" );
  }
}

short is_socket_inet(char *old_name, char *name) {
#ifdef CFG_JAVA_SOCKET_INET 
  return 1;
#endif
  return strcmp(old_name, name);
}
//static char line[] = "\na\n\nstr\n\n";
/**
 * Cuts a string into lines. Unlike strtok this can return empty
 * tokens:
 *
 * \na\n\nstr\n
 * -> 
 * -> a
 * -> 
 * -> str
 *
 * @param s the string or null
 * @return each new token, may be empty
 */
static char *linesep(char *s) {
  static const char chr = '\n';
  static char *str; if(s) str=s;
  char *pos = str, *c;
  if(pos) { 
    c = strchr(str, chr);
    // if we found a match and it is followed by \0, we're done
    if((str=c) && ((*str++=0),!*str)) str = 0;
  }
  return pos;
}
/**
 * Read "@channel\n" from the System.out.  Some insane VM
 * implementations (the Sun VM since 1.4.2) incorrectly write debug
 * output to System.out instead of System.err, so we have to deal with
 * this garbage. The Sun 1.5.0 VM for example writes "Listening for
 * transport dt_socket at address: 9147" if it was called with
 * -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:9147,server=y,suspend=n
 * the quiet=y option only exists in 1.6 and higher.
 */
#ifndef __MINGW32__
static char *readChannel(int fd, char*buf, size_t size) {
#else
static char *readChannel(HANDLE fd, char*buf, size_t size) {
  DWORD bwrite;
#endif
  ssize_t err;
  char *line, *next, delim;
  short contLine=0;
#ifndef __MINGW32__
  while((err=read(fd, buf, size-1))>0) {
#else
  while(ReadFile(fd, buf, size-1, &err, NULL) && err>0) {
#endif
    delim = buf[err-1];
    buf[err]=0;
    for(line=linesep(buf); line; line=next) {
      next = linesep(0);
      size_t len = strlen(line);
      if(len && line[0]=='@' && !contLine) {
		if(next || (delim=='\n')) return line;
		if(size<=len+1) return 0;
		memmove(buf, line, len);
#ifndef __MINGW32__
		err = read(fd, buf+len, size-len); if(err==-1) return 0;
#else
		if((!ReadFile(fd, buf+len, size-len, &err, NULL))||(err<=0)) return 0;
#endif
		len += err;
		next = strchr(buf, '\n'); 
		if(next) *next=0; else buf[len-1]=0;
		return buf;
      } else {
		/* we have recived garbage, most likely debug output from some
		   insane VM implementation, pass it on to System.err */
		size_t len = strlen(line);
		if(len) write(2, line, strlen(line));
		if(next || (!next && delim=='\n')) write(2, "\n", 1);
		contLine = 0;
      }
    }
    contLine = delim!='\n';
  }
  return 0;
}

/**
 * Start a VM as a sub process of the HTTP server
 */
void EXT_GLOBAL(start_server)(TSRMLS_D) {
  int pid=0, err=-1, p[2], st[2], stx;
  char buf[255], *channel = 0;
  char count, *test_server = 0, *name;
#ifndef __MINGW32__
  if(can_fork(TSRMLS_C) && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC)) && pipe(p)!=-1) {
	if(!(pid=fork())) {		/* daemon */
	  close(p[0]);
	  stx = pipe(st);
	  if(!fork()) {			/* guard */
		setsid();
		if(!(pid=fork())) {	/* java */
		  if(close(p[1])!=-1&& stx!=-1&& close(st[0])!=-1&& dup2(st[1], 1)!=-1)
			exec_vm(TSRMLS_C);
		  exit(105);
		}
		/* protect guard */
		signal(SIGHUP, SIG_IGN); 
		s_pid=pid; signal(SIGINT, s_kill); 
		signal(SIGTERM, SIG_IGN);
		
		write(p[1], &pid, sizeof pid);
		if(stx!=-1 && close(st[1])!=-1)
		  channel = readChannel(st[0], buf, sizeof buf);
		count = 0xFF & strlen(channel);
		write(p[1], &count, 1); if(count) write(p[1], channel, count);

		waitpid(pid, &err, 0);
		write(p[1], &err, sizeof err);
		exit(0);
	  } 
	  exit(0);
	}
	close(p[1]);
	wait(&err);
	if((read(p[0], &pid, sizeof pid))!=(sizeof pid)) pid=0;
	if((read(p[0], &count, 1))!=1) count=0;

	EXT_GLOBAL(cfg)->cid=pid;
	EXT_GLOBAL(cfg)->err=p[0];
	if(count&&((read(p[0], buf, sizeof buf))==count)) {
	  /* received channel # */
	  size_t n = count;
	  short inet;
	  n-=1;
	  name = malloc(n+1); if(!name) exit(9);
	  memcpy(name, buf+1, n); name[n]=0;
	  //php_printf("got server channel: %ld, %s", n, name);
	  inet = is_socket_inet(EXT_GLOBAL(cfg)->default_sockname, name);
	  free(EXT_GLOBAL(cfg)->default_sockname);
	  EXT_GLOBAL(cfg)->default_sockname=name;
	  make_local_socket_info(inet TSRMLS_CC);
	} else {
#ifdef CFG_JAVA_SOCKET_INET 
	  free(EXT_GLOBAL(cfg)->default_sockname);
	  EXT_GLOBAL(cfg)->default_sockname=strdup(DEFAULT_PORT);
	  assert(EXT_GLOBAL(cfg)->default_sockname); if(!EXT_GLOBAL(cfg)->default_sockname) exit(6);
	  make_local_socket_info(1 TSRMLS_CC);
#else
	  make_local_socket_info(0 TSRMLS_CC);
#endif
	  wait_server(TSRMLS_C);
	}
  } else 
#else
	if(can_fork(TSRMLS_C) && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC))) {
	  char *cmd = get_server_string(0 TSRMLS_CC);
	  DWORD properties = /*CREATE_NEW_CONSOLE | */CREATE_NEW_PROCESS_GROUP;
	  STARTUPINFO su_info;
	  HANDLE read_pipe, write_pipe, read_pipe_dup;
	  SECURITY_ATTRIBUTES pipe_sattr = {sizeof(SECURITY_ATTRIBUTES),NULL,TRUE};
	  HANDLE pid = GetCurrentProcess();
	  DWORD bread;
	  if(!CreatePipe(&read_pipe, &write_pipe, &pipe_sattr, 0)) {
		goto cannot_fork;
	  }
	  if(!DuplicateHandle(pid,read_pipe,pid,&read_pipe_dup,0,FALSE,DUPLICATE_SAME_ACCESS)) {
		CloseHandle(read_pipe);
		goto cannot_fork;
	  }
	  CloseHandle(read_pipe);

	  s_pid.use_wrapper = use_wrapper(EXT_GLOBAL(cfg)->wrapper);

	  ZeroMemory(&su_info, sizeof(STARTUPINFO));
	  su_info.cb = sizeof(STARTUPINFO);
	  su_info.dwFlags = STARTF_USESTDHANDLES;
	  su_info.hStdError	= GetStdHandle(STD_ERROR_HANDLE);
	  su_info.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
	  su_info.hStdOutput = write_pipe;
	  EXT_GLOBAL(cfg)->cid=0;
	  if(CreateProcess(NULL, cmd,
					   NULL, NULL, 1, properties, NULL, NULL, 
					   &su_info, &s_pid.p)) {
		CloseHandle(write_pipe);
		EXT_GLOBAL(cfg)->cid=s_pid.p.dwProcessId;
		if(channel=readChannel(read_pipe_dup, buf, sizeof buf)) {
		  count = strlen(channel)-1;
		  name = malloc(count+1); if(!name) exit(9);
		  memcpy(name, channel+1, count); name[count]=0;
		  free(EXT_GLOBAL(cfg)->default_sockname);
		  EXT_GLOBAL(cfg)->default_sockname=name;
		  make_local_socket_info(1 TSRMLS_CC);
		} else {
		  free(EXT_GLOBAL(cfg)->default_sockname);
		  EXT_GLOBAL(cfg)->default_sockname=strdup(DEFAULT_PORT);
		  assert(EXT_GLOBAL(cfg)->default_sockname); if(!EXT_GLOBAL(cfg)->default_sockname) exit(6);
		  make_local_socket_info(1 TSRMLS_CC);
		  wait_server(TSRMLS_C);
		}
		CloseHandle(s_pid.p.hThread);
		CloseHandle(read_pipe_dup);
	  } else {
	  cannot_fork:
		php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: Could not start back-end: %s; Code: %ld.", 105, cmd, (long)GetLastError());
		make_local_socket_info(1 TSRMLS_CC);
	  }
	  free(cmd);
	} else
#endif /* MINGW32 */
	  {
#ifdef CFG_JAVA_SOCKET_INET 
		make_local_socket_info(1 TSRMLS_CC);
#else
		make_local_socket_info(0 TSRMLS_CC);
#endif
		EXT_GLOBAL(cfg)->cid=EXT_GLOBAL(cfg)->err=0;
	  }
  if(test_server) free(test_server);
}

static void wait_for_daemon(void) {
#ifndef __MINGW32__
  static const int sig[] = {SIGTERM, SIGKILL};
  fd_set rfds;
  int err, i;

  assert(EXT_GLOBAL(cfg)->err); if(!(EXT_GLOBAL(cfg)->err)) return;
  assert(EXT_GLOBAL(cfg)->cid);

  /* first kill is trapped, second kill is received with default
	 handler. If the server still exists, we send it a -9 */
  kill(EXT_GLOBAL(cfg)->cid, SIGTERM);
  FD_ZERO(&rfds);
  FD_SET(EXT_GLOBAL(cfg)->err, &rfds);
  for(i=0; i<2; i++) {
	struct timeval timeval = {2l, 0};
	if(select(1+EXT_GLOBAL(cfg)->err, &rfds, 0, 0, &timeval) > 0) break;
	kill(EXT_GLOBAL(cfg)->cid, sig[i]);
  }	

  if((read(EXT_GLOBAL(cfg)->err, &err, sizeof err))!=sizeof err) err=0;
  //printf("VM terminated with code: %ld\n", err);
  close(EXT_GLOBAL(cfg)->err);
  EXT_GLOBAL(cfg)->err=0;
#else
  s_kill(0);					/* always -9 on windows */
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

/**
 * Called when the FCGI master has received the kill signal.
 */
static void fcgi_do_rmtmpdir() {
#ifndef __MINGW32__
  extern EXT_GLOBAL(is_parent);
  static const char lck[] = ".lck";
  DIR * dir;
  struct dirent * file;
  size_t len;
  char *name, *lock_name;
  int lock_file, err;

  /* If this is not an fcgi child, do nothing. */
  if(!EXT_GLOBAL(cfg) || !EXT_GLOBAL(cfg)->tmpdir) return;

  len = strlen(EXT_GLOBAL(cfg)->tmpdir);

  lock_name = malloc(len+sizeof lck);
  strcpy(lock_name, (EXT_GLOBAL(cfg)->tmpdir));
  strcat(lock_name, lck);
  lock_file = open(lock_name, O_CREAT | O_EXCL, 0700);
  unlink(lock_name);
  free(lock_name);
  if(lock_file==-1) return;

  dir = opendir(EXT_GLOBAL(cfg)->tmpdir);
  if(!dir) return;
  while(file = readdir(dir)) {
	if(file->d_name[0]!='p') continue;
	name = malloc(len + strlen(file->d_name)+2);
	if(!name) exit(6);
	strcpy(name, (EXT_GLOBAL(cfg)->tmpdir));
	strcat(name, "/");
	strcat(name, file->d_name);
	unlink(name);
	free(name);
  }
  closedir(dir);
  rmdir(EXT_GLOBAL(cfg)->tmpdir);
  free(EXT_GLOBAL(cfg)->tmpdir);
  EXT_GLOBAL(cfg)->tmpdir=0;
#endif
}
/** 
 * Signal handler for FastCGI
 */
static void fcgi_rmtmpdir(int sig) {
  fcgi_do_rmtmpdir();
  exit(0);
}
/**
 * Delete the temp directory which contains the comm. pipes 
 */
static void rmtmpdir () {
#ifndef __MINGW32__
  extern EXT_GLOBAL(is_parent);
  DIR * dir;
  struct dirent * file;
  size_t len;
  char *name;
  int err;
  int lock;

  /* If this is a child, do nothing. */
  if(!EXT_GLOBAL(cfg) || !EXT_GLOBAL(cfg)->tmpdir || EXT_GLOBAL(cfg)->pid!=getpid()) return;
  len = strlen(EXT_GLOBAL(cfg)->tmpdir);
  dir = opendir(EXT_GLOBAL(cfg)->tmpdir);
  if(!dir) { EXT_GLOBAL(sys_error)("Could not open tmpdir", 65); return; }
  while(file = readdir(dir)) {
	if(file->d_name[0]!='p') continue;
	name = malloc(len + strlen(file->d_name)+2);
	if(!name) exit(6);
	strcpy(name, (EXT_GLOBAL(cfg)->tmpdir));
	strcat(name, "/");
	strcat(name, file->d_name);
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): Removing %s which is not (yet?) connected. ", 66, name);
	unlink(name);
	free(name);
  }
  err = closedir(dir);
  if(err==-1) { EXT_GLOBAL(sys_error)("Could not close tmpdir", 67); return; }

  err = rmdir(EXT_GLOBAL(cfg)->tmpdir);
  if(err==-1) { EXT_GLOBAL(sys_error)("Could not unlink tmpdir", 68); return; }

  free(EXT_GLOBAL(cfg)->tmpdir);
  EXT_GLOBAL(cfg)->tmpdir=0;
#endif
}
/**
 * Called from MSHUTDOWN. This method does nothing if this is a
 * FastCGI servlet, because the FCGI SAPI calls MSHUTDOWN everytime a
 * child is killed, which is nonsense, of course.
 */
void EXT_GLOBAL(rmtmpdir) () {
								/* see FastCGI comment below */
  if(!EXT_GLOBAL(cfg)->is_fcgi_servlet) rmtmpdir();	
}
/**
 * Wrapper for mkdtemp().
 */
#ifndef __MINGW32__
static char *makedtemp(char tmpl[]) {
#ifdef HAVE_MKDTEMP
  char *str = 0, *s = mkdtemp (tmpl);
  if(s) if(!(str=strdup(s))) { rmdir(s); exit(6); }
  return str;
#else
  char *s, *p = strrchr(tmpl, '/');
  char c = *p;
  *p=0; 
  p[6]=0; 
  s = tempnam(tmpl,p+1); 
  *p=c;
  if(!s) return 0;
  if(-1==mkdir(s, 0700)) { free(s); return 0; }
  return s;
#endif
}
#endif
/**
 * Called from MINIT, creates a directory which will contain the
 * comm. pipes on Unix. See rmtmpdir above.
 */
void EXT_GLOBAL(mktmpdir) () {
#ifndef __MINGW32__
  char sockname[] = SOCKNAME;
  char sockname_shm[] = SOCKNAME_SHM;
  char *tmpdir;

  /* The FastCGI SAPI is completely odd, it calls the parent(!)
	 mshutdown for each killed child. Ignore this nonsense and call
	 rmtmpdir ourselfs when the parent exits. */
  if(EXT_GLOBAL(cfg)->is_fcgi_servlet) signal(SIGTERM, fcgi_rmtmpdir); 

  tmpdir = makedtemp(sockname_shm);
  if(!tmpdir) tmpdir = makedtemp(sockname);
  if(!tmpdir) {EXT_GLOBAL(cfg)->tmpdir=0; return;}
  EXT_GLOBAL(cfg)->tmpdir=tmpdir;
  chmod(tmpdir, 01777);
#else  /* There's no standard tmpdir on windows */
  EXT_GLOBAL(cfg)->tmpdir=0;
#endif
}
#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
