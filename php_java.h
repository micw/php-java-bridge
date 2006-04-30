/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * This is the main entry point for the java extension. 

 * It contains the global structures and the callbacks required for
 * zend engine 1 and 2.
 *
 */

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
#include <sys/stat.h>
#include <fcntl.h>

/* socket */
#ifdef __MINGW32__
# include <winsock2.h>
# define close closesocket
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

/* disable unix domain sockets if jni is not available */
#ifndef HAVE_JNI
# ifndef CFG_JAVA_SOCKET_INET
#  define CFG_JAVA_SOCKET_INET
# endif
#endif
# if defined(CFG_JAVA_SOCKET_INET) && !defined(HAVE_FAST_TCP_SOCKETS)
#  warning Local TCP sockets are very slow on this system, the J2EE component will be slower than necessary (see unsupported/TestServ.c). Use unix domain sockets instead (requires JNI).
# endif

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

#ifdef ZEND_ENGINE_2
/* for try/catch emulation */
#include <setjmp.h>
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

extern int EXT_GLOBAL(ini_updated), EXT_GLOBAL (ini_user), EXT_GLOBAL (ini_set);
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
#define U_EXT_JAVA_COMPATIBILITY  (1<<11)
#define U_PERSISTENT_CONNECTIONS  (1<<12)

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

/**
 * The following structure contains shared variables.
 */
struct cfg {
#ifdef CFG_JAVA_SOCKET_INET
  struct sockaddr_in saddr;
#else
  /** The socket address of the backend, used when java.socketname is set.*/
  struct sockaddr_un saddr;
#endif
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
  /** 0: Compatibility with ext/java is off (default), 1: on  */
  short extJavaCompatibility;
  short persistent_connections;
};
extern struct cfg *EXT_GLOBAL(cfg);

/**
 * The following structure contains per-request variables.
 */
EXT_BEGIN_MODULE_GLOBALS(EXT)
  proxyenv *jenv;
  short is_closed; /* PR1176522: GC must not re-open the connection */

  /* local copy of the shared variables above. Needed for channel
	 re-directs */
  char *hosts, *servlet, *redirect_port;
  int ini_user;

  /* the name of the comm. pipe */
  char*channel, *channel_in, *channel_out;
  int lockfile;

  /* for user CB's */
  zval*exception;

  zval **object;
  zval *func;
  zval **retval_ptr;
  zval *func_params;
EXT_END_MODULE_GLOBALS(EXT)




#ifdef ZTS
# define JG(v) EXT_TSRMG(EXT_GLOBAL(globals_id), EXT_GLOBAL_EX(zend_,, _globals) *, v)
#else
# define JG(v) EXT_GLOBAL(globals).v
#endif

extern char* EXT_GLOBAL(get_server_string)(TSRMLS_D);
extern proxyenv *EXT_GLOBAL(try_connect_to_server)(TSRMLS_D);
extern proxyenv *EXT_GLOBAL(connect_to_server)(TSRMLS_D);
extern void EXT_GLOBAL(close_connection)(proxyenv**env, short persistent_connection TSRMLS_DC);
extern void EXT_GLOBAL(start_server)(TSRMLS_D);
extern void EXT_GLOBAL(clone_cfg)(TSRMLS_D);
extern void EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_D);

extern char* EXT_GLOBAL(test_server)(int *socket, short *is_local, struct sockaddr*saddr TSRMLS_DC);

/* returns the servlet context or null */
extern char *EXT_GLOBAL(get_servlet_context)(TSRMLS_D);

/* returns the local socketname or the default local socketname*/
extern char *EXT_GLOBAL(get_sockname)(TSRMLS_D);

#endif
