/*-*- mode: C; tab-width:4 -*-*/

/*
 * php_java.h -- the main interface of the PHP/Java Bridge.

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

#ifndef PHP_JAVA_H
#define PHP_JAVA_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php_wrapper.h"
#include "php_config.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"
#include "protocol.h"
#ifdef ZTS
#include "TSRM.h"
#endif

#include <sys/types.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>

/* socket */
#ifdef __MINGW32__
# include <winsock2.h>
# define close closesocket
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>

/* disable unix domain sockets if jni is not available */
#ifndef HAVE_JNI
# ifndef CFG_JAVA_SOCKET_INET
#  define CFG_JAVA_SOCKET_INET
# endif
#endif

#ifndef CFG_JAVA_SOCKET_INET
# include <sys/un.h>
# ifdef HAVE_CONFIG_H
# if !HAVE_DECL_AF_LOCAL
#  define AF_LOCAL AF_UNIX
# endif
# if !HAVE_DECL_PF_LOCAL
#  define PF_LOCAL PF_UNIX
# endif
# endif
#endif
#endif 

extern zend_module_entry EXT_GLOBAL(module_entry);
extern zend_class_entry *EXT_GLOBAL(class_entry);
extern zend_class_entry *EXT_GLOBAL(array_entry);
extern zend_class_entry *EXT_GLOBAL(class_class_entry);
extern zend_class_entry *EXT_GLOBAL(class_class_entry_jsr);
extern zend_class_entry *EXT_GLOBAL(exception_class_entry);
extern function_entry EXT_GLOBAL(class_functions[]);

#ifdef ZEND_ENGINE_2
extern zend_object_handlers EXT_GLOBAL(handlers);
#endif
extern const char * const EXT_GLOBAL(bridge_version);

extern int EXT_GLOBAL(ini_updated), EXT_GLOBAL (ini_user), 
  EXT_GLOBAL (ini_set), EXT_GLOBAL(ini_override);

#define U_LOGFILE (1<<1)
#define U_LOGLEVEL (1<<2)
#define U_JAVA_HOME (1<<3)
#define U_JAVA (1<<4)
#define U_LIBRARY_PATH (1<<5)
#define U_CLASSPATH (1<<6)
#define U_SOCKNAME (1<<7)
#define U_HOSTS (1<<8)
#define U_SERVLET (1<<9)
#define U_WRAPPER (1<<10)
#define U_SECURE (1<<11) /* if X_JAVABRIDGE_OVERRIDE contains a s: prefix */
#define U_PERSISTENT_CONNECTIONS  (1<<12)
#define U_POLICY (1<<13)

#if EXTENSION == JAVA
#define phpext_java_ptr &EXT_GLOBAL(module_entry)
#elif EXTENSION == MONO
#define phpext_mono_ptr &EXT_GLOBAL(module_entry)
#else
# error EXT must be mono or java.
#endif


PHP_MINIT_FUNCTION(EXT);
PHP_MSHUTDOWN_FUNCTION(EXT);
PHP_MINFO_FUNCTION(EXT);

struct cfg {

  /** The socket address of the backend, used when java.socketname is set.*/
  union {
	struct sockaddr_in in;
#ifndef CFG_JAVA_SOCKET_INET
	struct sockaddr_un un;
#endif
  } saddr;

  /** The process id of the backend, used when java.socketname, java.hosts and java.servlet are off */
  int cid; // server's process id
  /** The file descriptor of the backend, used when java.socketname, java.hosts and java.servlet are off */
  int err; // file descriptor: server's return code

  /** The java.socketname */
  char*sockname;
  /** The default socketname */
  char *default_sockname;
  /** The java.hosts list */
  char*hosts;
  /** The java.classpath */
  char*classpath;	
  /** The java.libpath*/
  char*ld_library_path;
  /** The java.wrapper */
  char*wrapper;
  /** The java.policy */
  char*policy;
  /** The java.java */
  char*vm;
  /** The java.java_home */
  char*vm_home;
  /** The java.log_level */
  char*logLevel;
  /** The java.log_level as a number */
  unsigned short logLevel_val;
  /** The java.log_file */
  char*logFile;
  /** 1: if java.socketname, java.hosts and java.servlet are not set */
  short can_fork;				/* 0 if user has hard-coded the socketname */
  /** The java.servlet, defaults to /JavaBridge/JavaBridge.php */
  char* servlet;				/* On or servlet context */
  short servlet_is_default;		/* If java.servlet=On */
  /** When the environment variable X_JAVABRIDGE_OVERRIDE_HOSTS is set
	  to "/" or "host:port//Context/Servlet, the values java.servlet
	  and java.hosts are taken from this environment variable. Used by
	  FastCGI and CGI only. */
  short is_cgi_servlet, is_fcgi_servlet; /* 1: cgi env available */
  /** 1: local backend; 0: backend from the host list */
  short socketname_set;
  short persistent_connections;

  /* the temporary directory for the named pipes */
  char *tmpdir;

  /* set to 1 if the back-end uses TCP sockets, 0 otherwise */
  short java_socket_inet;

#ifndef __MINGW32__
  pid_t pid; 						/* the pid of the group leader or 0 */
#endif
};
extern struct cfg *EXT_GLOBAL(cfg);


/* for user CB's */
struct cb_stack_elem {
  zval*exception;
  zval **object;
  zval *func;
  zval **retval_ptr;
  zval *func_params;
};

/**
 * The following structure contains per-request variables.
 */
EXT_BEGIN_MODULE_GLOBALS(EXT)
  proxyenv *jenv;
  short is_closed; /* PR1176522: GC must not re-open the connection */

  /* local copy of the shared variables above. Needed for channel
	 re-directs */
  char *hosts, *servlet;
  char *redirect_port; 			/* the port sub-string from hosts */
  int ini_user;
  short java_socket_inet;		/* copy of java_socket_inet */

  /* for user CB's */
  zend_stack *cb_stack;

  /* mapping of servlet context strings to persistent connections */
  HashTable connections;

  /* copy of the connection variables. Other connections
   may share these and set (*env)->is_shared=1 */
  int peer, peerr;
  char *servlet_ctx;
EXT_END_MODULE_GLOBALS(EXT)


#ifdef ZTS
# define JG(v) EXT_TSRMG(EXT_GLOBAL(globals_id), EXT_GLOBAL_EX(zend_,, _globals) *, v)
#else
# define JG(v) EXT_GLOBAL(globals).v
#endif

extern char* EXT_GLOBAL(get_server_string)(TSRMLS_D);
extern void EXT_GLOBAL(override_ini_for_redirect)(TSRMLS_D);
extern proxyenv *EXT_GLOBAL(try_connect_to_server)(TSRMLS_D);
extern proxyenv *EXT_GLOBAL(connect_to_server)(TSRMLS_D);
extern short EXT_GLOBAL(close_connection)(proxyenv*env, short persistent_connection TSRMLS_DC);
extern void EXT_GLOBAL(start_server)(TSRMLS_D);
extern void EXT_GLOBAL(activate_connection)(proxyenv *env TSRMLS_DC);
extern void EXT_GLOBAL(passivate_connection)(proxyenv *env TSRMLS_DC);
extern void EXT_GLOBAL(clone_cfg)(TSRMLS_D);
extern void EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_D);

extern char* EXT_GLOBAL(test_server)(int *socket, short *is_local, struct sockaddr*saddr TSRMLS_DC);

/* returns the servlet context or null */
extern char *EXT_GLOBAL(get_servlet_context)(TSRMLS_D);

/* returns the local socketname or the default local socketname*/
extern char *EXT_GLOBAL(get_sockname)(TSRMLS_D);

#endif
