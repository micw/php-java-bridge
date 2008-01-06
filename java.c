/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * This is the main entry point for the java extension. 

  It contains the global structures and the callbacks required for
  zend engine 1 and 2.

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
  exception statement from your version.
 */

#include "zend.h"
#include "init_cfg.h"
#if !defined(ZEND_ENGINE_2)
# error "PHP 4 is not supported anymore. Use php-java-bridge-4.3.2 instead"
#else

#include "php_java.h"

/* wait */
#include <sys/types.h>
#include <sys/wait.h>
/* miscellaneous */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include "php_globals.h"
#include "ext/standard/info.h"

#include "zend_extensions.h"
#include "TSRM.h"

#include "init_cfg.h"

EXT_DECLARE_MODULE_GLOBALS(EXT)

/**
 * Holds the global configuration.
 * This structure is shared by all php instances
 */
struct cfg *EXT_GLOBAL (cfg)  = 0;

#ifdef __MINGW32__
static const int java_errno=0;
int *__errno (void) { return (int*)&java_errno; }
#ifdef ZEND_ENGINE_2
#define php_info_print_table_row(a, b, c) php_info_print_table_row_ex(a, "v", b, c)
#else
#define php_info_print_table_end() php_printf("</table><br />\n")
#endif
#endif


#if EXTENSION == JAVA
extern char java_inc[];
extern size_t java_inc_length();
static size_t java_stream_reader(void *handle, char *buf, size_t len TSRMLS_DC) {
  size_t end = java_inc_length()-1;
  size_t *pos = (size_t*)handle;
  size_t remain = end-*pos;
  if(end>*pos) {
	if(remain<len) len=remain;
	memcpy(buf, java_inc+*pos, len);
	*pos+=len;
	return len;
  }
  return 0;
}
#else
extern char mono_inc[];
extern size_t mono_inc_length();
static size_t java_stream_reader(void *handle, char *buf, size_t len TSRMLS_DC) {
  size_t end = mono_inc_length()-1;
  size_t *pos = (size_t*)handle;
  size_t remain = end-*pos;
  if(end>*pos) {
	if(remain<len) len=remain;
	memcpy(buf, mono_inc+*pos, len);
	*pos+=len;
	return len;
  }
  return 0;
}
#endif
static void java_stream_closer(void *handle TSRMLS_DC) {
}
static long java_stream_fteller(void *handle TSRMLS_DC) {
  return (long)*(size_t*)handle;
}

static zend_op_array *java_compile_string(char*name TSRMLS_DC) {
  zend_file_handle file_handle = {0};
  zend_stream stream = {0};
  size_t pos = 0;
  zend_op_array *array;

  stream.handle = &pos;
  stream.reader = java_stream_reader;
  stream.closer = java_stream_closer;
#if ZEND_EXTENSION_API_NO >= 220051025
  stream.fteller = java_stream_fteller;
#endif
  file_handle.type = ZEND_HANDLE_STREAM;
  file_handle.filename = name;
  file_handle.free_filename = 0;
  file_handle.handle.stream = stream;
  array = zend_compile_file(&file_handle, ZEND_REQUIRE_ONCE TSRMLS_CC);
  zend_destroy_file_handle(&file_handle TSRMLS_CC);
  return array;
}
  
PHP_RINIT_FUNCTION(EXT) 
{
  static zend_op_array *ar;
  zend_op_array *current;
  zval *result = 0;

  if(!ar) {
	ar = java_compile_string(EXT_NAME()/**/".inc" TSRMLS_CC);
  }
  if(!ar) abort();
  EG(return_value_ptr_ptr) = &result;
  current = EG(active_op_array);
  EG(active_op_array) = ar;
  zend_execute(ar TSRMLS_CC);
#if 1
  destroy_op_array(ar TSRMLS_CC);
  efree(ar);
  ar = 0;
#endif
  if (!EG(exception)) {
	if (EG(return_value_ptr_ptr)) {
	  zval_ptr_dtor(EG(return_value_ptr_ptr));
	}
  }
  EG(active_op_array) = current;
  return SUCCESS;
}

/**
 * Called when the request terminates. Closes the connection to the
 * back-end, destroys the proxyenv instance.
 */
PHP_RSHUTDOWN_FUNCTION(EXT)
{
  return SUCCESS;
}

EXT_FUNCTION(EXT_GLOBAL(get_default_channel))
{
  if(EXT_GLOBAL(can_fork)(TSRMLS_C) && (EXT_GLOBAL(cfg)->default_sockname)) {
	char *name = EXT_GLOBAL(cfg)->default_sockname;
	if(name[0]=='@' || name[0]=='/') { /* unix domain socket */
	  RETURN_STRING(name, 1);
	} else {					/* tcp socket */
	  RETURN_LONG(atoi(EXT_GLOBAL(cfg)->default_sockname));
	}
  } else {
	RETURN_NULL();
  }
}

#ifndef GENERATE_DOC
function_entry EXT_GLOBAL(functions)[] = {
  EXT_FE(EXT_GLOBAL(get_default_channel), NULL)
  {NULL, NULL, NULL}
};

zend_module_entry EXT_GLOBAL(module_entry) = {
  STANDARD_MODULE_HEADER,
  EXT_NAME(),
  EXT_GLOBAL(functions),
  EXT_MINIT(EXT),
  EXT_MSHUTDOWN(EXT),
  EXT_RINIT(EXT),
  EXT_RSHUTDOWN(EXT),
  EXT_MINFO(EXT),
  NO_VERSION_YET,
  STANDARD_MODULE_PROPERTIES
};
#endif /* !GENERATE_DOC */

#if defined(COMPILE_DL_JAVA) || defined(COMPILE_DL_MONO)
EXT_GET_MODULE(EXT)
#endif

/**
 * Holds the flags set/unset for all overridden java ini entries
 * these are U_HOST, U_SERVLET and U_SOCKNAME
 * @see X_JAVABRIDGE_OVERRIDE_HOSTS
 */
int EXT_GLOBAL(ini_override);

/**
 * Holds the flags set/unset for all java ini entries
 */
int EXT_GLOBAL(ini_updated);

/**
 * The options set by the user.
 */
int EXT_GLOBAL(ini_user);

/**
 * The options which carry a value.
 */
int EXT_GLOBAL(ini_set);

static PHP_INI_MH(OnIniPolicy)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_POLICY)) free(EXT_GLOBAL(cfg)->policy);
	EXT_GLOBAL(cfg)->policy=strdup(new_value);
	assert(EXT_GLOBAL(cfg)->policy); if(!EXT_GLOBAL(cfg)->policy) exit(6);
	EXT_GLOBAL(ini_updated)|=U_POLICY;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniWrapper)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_WRAPPER)) free(EXT_GLOBAL(cfg)->wrapper);
	EXT_GLOBAL(cfg)->wrapper=strdup(new_value);
	assert(EXT_GLOBAL(cfg)->wrapper); if(!EXT_GLOBAL(cfg)->wrapper) exit(6);
	EXT_GLOBAL(ini_updated)|=U_WRAPPER;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniHosts)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_HOSTS)) {
	EXT_GLOBAL(update_hosts)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniServlet)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_SERVLET)) {
	EXT_GLOBAL(update_servlet)(new_value);
  }
  return SUCCESS;
}

static PHP_INI_MH(OnIniSockname)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_SOCKNAME)) {
	EXT_GLOBAL(update_socketname)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_CLASSPATH)) free(EXT_GLOBAL(cfg)->classpath);
	EXT_GLOBAL(cfg)->classpath =strdup(new_value);
	assert(EXT_GLOBAL(cfg)->classpath); if(!EXT_GLOBAL(cfg)->classpath) exit(6);
	EXT_GLOBAL(ini_updated)|=U_CLASSPATH;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LIBRARY_PATH)) free(EXT_GLOBAL(cfg)->ld_library_path);
	EXT_GLOBAL(cfg)->ld_library_path = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->ld_library_path); if(!EXT_GLOBAL(cfg)->ld_library_path) exit(6);
	EXT_GLOBAL(ini_updated)|=U_LIBRARY_PATH;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_JAVA)) free(EXT_GLOBAL(cfg)->vm);
	EXT_GLOBAL(cfg)->vm = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->vm); if(!EXT_GLOBAL(cfg)->vm) exit(6);
	EXT_GLOBAL(ini_updated)|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_JAVA_HOME)) free(EXT_GLOBAL(cfg)->vm_home);
	EXT_GLOBAL(cfg)->vm_home = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->vm_home); if(!EXT_GLOBAL(cfg)->vm_home) exit(6);
	EXT_GLOBAL(ini_updated)|=U_JAVA_HOME;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LOGLEVEL)) free(EXT_GLOBAL(cfg)->logLevel);
	EXT_GLOBAL(cfg)->logLevel = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->logLevel); if(!EXT_GLOBAL(cfg)->logLevel) exit(6);
	EXT_GLOBAL(cfg)->logLevel_val=atoi(EXT_GLOBAL(cfg)->logLevel);
	EXT_GLOBAL(ini_updated)|=U_LOGLEVEL;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LOGFILE)) free(EXT_GLOBAL(cfg)->logFile);
	EXT_GLOBAL(cfg)->logFile = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->logFile); if(!EXT_GLOBAL(cfg)->logFile) exit(6);
	EXT_GLOBAL(ini_updated)|=U_LOGFILE;
  }
  return SUCCESS;
}
PHP_INI_BEGIN()
  PHP_INI_ENTRY(EXT_NAME()/**/".servlet", NULL, PHP_INI_SYSTEM, OnIniServlet)
  PHP_INI_ENTRY(EXT_NAME()/**/".socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
  PHP_INI_ENTRY(EXT_NAME()/**/".hosts",   NULL, PHP_INI_SYSTEM, OnIniHosts)
  PHP_INI_ENTRY(EXT_NAME()/**/".wrapper",   NULL, PHP_INI_SYSTEM, OnIniWrapper)
  PHP_INI_ENTRY(EXT_NAME()/**/".security_policy",   NULL, PHP_INI_SYSTEM, OnIniPolicy)
  PHP_INI_ENTRY(EXT_NAME()/**/".classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
  PHP_INI_ENTRY(EXT_NAME()/**/".libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"",   NULL, PHP_INI_SYSTEM, OnIniJava)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

  PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
  PHP_INI_ENTRY(EXT_NAME()/**/".log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
  PHP_INI_END()

/* PREFORK calls this once. All childs receive cloned values. However,
   the WORKER MPM calls this for the master and for all childs */
  static void EXT_GLOBAL(alloc_globals_ctor)(EXT_GLOBAL_EX(zend_,globals,_) *EXT_GLOBAL(globals) TSRMLS_DC)
{
}


void EXT_GLOBAL(clone_cfg)(TSRMLS_D) {
  JG(ini_user)=EXT_GLOBAL(ini_user);
  JG(java_socket_inet) = EXT_GLOBAL(cfg)->java_socket_inet;
  if(JG(hosts)) free(JG(hosts));
  if(!(JG(hosts)=strdup(EXT_GLOBAL(cfg)->hosts))) exit(9);
  if(JG(servlet)) free(JG(servlet));
  if(!(JG(servlet)=strdup(EXT_GLOBAL(cfg)->servlet))) exit(9);
}
void EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_D) {
  if(JG(hosts)) free(JG(hosts));
  if(JG(servlet)) free(JG(servlet));
  JG(ini_user)=0;
  JG(java_socket_inet)=0;
  JG(hosts)=0;
  JG(servlet)=0;
}

/**
 * Called when the module is initialized. Creates the Java and
 * JavaClass structures and tries to start the back-end if
 * java.socketname, java.servlet or java.hosts are not set.  The
 * back-end is not started if the environment variable
 * X_JAVABRIDGE_OVERRIDE_HOSTS exists and contains either "/" or
 * "host:port//context/servlet".  When running as a Apache/IIS module
 * or Fast CGI, this procedure is called only once. When running as a
 * CGI binary, it is called whenever the CGI binary is called.
 */
PHP_MINIT_FUNCTION(EXT)
{
  zend_class_entry *parent;
  
  EXT_INIT_MODULE_GLOBALS(EXT, EXT_GLOBAL(alloc_globals_ctor), NULL);
  
  assert(!EXT_GLOBAL (cfg) );
  if(!EXT_GLOBAL (cfg) ) EXT_GLOBAL (cfg) = malloc(sizeof *EXT_GLOBAL (cfg) ); 
  if(!EXT_GLOBAL (cfg) ) exit(9);

  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	char *tmpdir, sockname_shm[] = SOCKNAME_SHM, sockname[] = SOCKNAME;
	/* set the default values for all undefined */
	
	EXT_GLOBAL(init_cfg) (TSRMLS_C);

	EXT_GLOBAL(clone_cfg)(TSRMLS_C);
	EXT_GLOBAL(start_server) (TSRMLS_C);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
	EXT_GLOBAL(mktmpdir)();
  } 

  /* 
   * don't bother setting JAVA_HOSTS if this is a fcgi servlet
   * (getenv("X_JAVABRIDGE_OVERRIDE_HOSTS")=="/") as the servlet will
   * set X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT anyway
   */
  if(EXT_GLOBAL(option_set_by_user)(U_HOSTS, EXT_GLOBAL(ini_user)) && !(EXT_GLOBAL(cfg)->is_fcgi_servlet)) {
	REGISTER_STRING_CONSTANT(EXTU/**/"_HOSTS", EXT_GLOBAL(cfg)->hosts, CONST_CS | CONST_PERSISTENT);
	if(EXT_GLOBAL(option_set_by_user)(U_SERVLET, EXT_GLOBAL(ini_user)))
	  REGISTER_STRING_CONSTANT(EXTU/**/"_SERVLET", EXT_GLOBAL(cfg)->servlet, CONST_CS | CONST_PERSISTENT);
  }

  REGISTER_STRING_CONSTANT(EXTU/**/"_PIPE_DIR", EXT_GLOBAL(cfg)->tmpdir, CONST_CS | CONST_PERSISTENT);

  return SUCCESS;
}
/**
 * A stack element which keeps the current cfg.
 */
struct save_cfg {
  /** A copy of the ini options set by the user */
  int ini_user;
  /** A copy of servlet context */
  char *servlet;
  /** A copy of the host list */
  char *hosts;
  short java_socket_inet;
};
static void push_cfg(struct save_cfg*cfg TSRMLS_DC) {
  cfg->ini_user = JG(ini_user);
  cfg->java_socket_inet = JG(java_socket_inet);
  cfg->servlet = JG(servlet);
  cfg->hosts = JG(hosts);
  JG(ini_user) = EXT_GLOBAL(ini_user);
  if(!(JG(hosts)=strdup(EXT_GLOBAL(cfg)->hosts))) exit(9);
  if(!(JG(servlet)=strdup(EXT_GLOBAL(cfg)->servlet))) exit(9);
}
static void pop_cfg(struct save_cfg*cfg TSRMLS_DC) {
  JG(ini_user) = cfg->ini_user;
  JG(java_socket_inet) = cfg->java_socket_inet;
  if(JG(servlet)) free(JG(servlet)); 
  JG(servlet) = cfg->servlet;
  if(JG(hosts)) free(JG(hosts)); 
  JG(hosts) = cfg->hosts;
}

/**
 * Displays the module info.
 */
PHP_MINFO_FUNCTION(EXT)
{
  static const char on[]="On";
  static const char off[]="Off";
  short is_local=0, is_level;
  char*s, *server=0;
  struct save_cfg saved_cfg;

  push_cfg(&saved_cfg TSRMLS_CC);
  EXT_GLOBAL(clone_cfg)(TSRMLS_C);
  s = EXT_GLOBAL(get_server_string) (TSRMLS_C);

  if(!EXT_GLOBAL(cfg)->is_fcgi_servlet)
	server = EXT_GLOBAL(test_server) (0, &is_local, 0 TSRMLS_CC);
  else {						/* we don't own the back end */
	zval retval;
	static const char str[]=EXT_NAME()/**/"_server_name();";
	static const char name[]=EXT_NAME()/**/"_server_name";
	int val = zend_eval_string((char*)str, &retval, (char*)name TSRMLS_CC);
	if(SUCCESS==val && (Z_TYPE(retval)==IS_STRING)) server = strdup(Z_STRVAL(retval));
  }
  is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);

  php_info_print_table_start();
  php_info_print_table_row(2, EXT_NAME()/**/" support", "Enabled");
  php_info_print_table_row(2, EXT_NAME()/**/" bridge", EXT_GLOBAL(bridge_version));
#if EXTENSION == JAVA
  if(!server || is_local) {
								/* don't show default value, they may
								   not be used anyway */
	if((EXT_GLOBAL(option_set_by_user) (U_LIBRARY_PATH, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".libpath", EXT_GLOBAL(cfg)->ld_library_path);

								/* don't show default value, they may
								   not be used anyway */
	if((EXT_GLOBAL(option_set_by_user) (U_CLASSPATH, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".classpath", EXT_GLOBAL(cfg)->classpath);
  }
#endif
  if(!server || is_local) {
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home", EXT_GLOBAL(cfg)->vm_home);
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME(), EXT_GLOBAL(cfg)->vm);
	if((EXT_GLOBAL(option_set_by_user) (U_WRAPPER, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".wrapper", EXT_GLOBAL(cfg)->wrapper);
	if(strlen(EXT_GLOBAL(cfg)->logFile)==0) 
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", "<stderr>");
	else
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", EXT_GLOBAL(cfg)->logFile);
	
	php_info_print_table_row(2, EXT_NAME()/**/".log_level", is_level ? EXT_GLOBAL(cfg)->logLevel : "no value (use back-end's default level)");
	if(EXT_GLOBAL(option_set_by_user) (U_HOSTS, JG(ini_user)))  
	  php_info_print_table_row(2, EXT_NAME()/**/".hosts", JG(hosts));
	if(!EXT_GLOBAL(cfg)->policy) {
	  php_info_print_table_row(2, EXT_NAME()/**/".security_policy", "Off");
	} else {
	  /* set by user */
	  if(EXT_GLOBAL(option_set_by_user) (U_POLICY, EXT_GLOBAL(ini_user)))
		php_info_print_table_row(2, EXT_NAME()/**/".security_policy", EXT_GLOBAL(cfg)->policy);
	  else
		php_info_print_table_row(2, EXT_NAME()/**/".security_policy", "Off");
	}
	php_info_print_table_row(2, EXT_NAME()/**/" command", s);
  }
  php_info_print_table_row(2, EXT_NAME()/**/" server", server?server:"localhost");
  php_info_print_table_row(2, EXT_NAME()/**/" status", server?"running":"not running");
  php_info_print_table_end();
  
  free(server);
  free(s);
  EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
  pop_cfg(&saved_cfg TSRMLS_CC);
}

/**
 * Called when the module terminates. Stops the back-end, if it is running.
 * When running in Apache/IIS or as a FastCGI binary, this procedure is 
 * called only once. When running as a CGI binary this is called whenever
 * the CGI binary terminates.
 */
PHP_MSHUTDOWN_FUNCTION(EXT) 
{
  EXT_GLOBAL(destroy_cfg) (EXT_GLOBAL(ini_set));
  EXT_GLOBAL(ini_user) = EXT_GLOBAL(ini_set) = 0;

  UNREGISTER_INI_ENTRIES();
  EXT_GLOBAL(shutdown_library) ();

  assert(EXT_GLOBAL (cfg));
  if(EXT_GLOBAL (cfg) ) { 
	EXT_GLOBAL(rmtmpdir)();
	free(EXT_GLOBAL (cfg) ); EXT_GLOBAL (cfg) = 0; 
  }
	
  return SUCCESS;
}



/** Zend module stuff */

int EXT_GLOBAL(zend_startup)(zend_extension *extension)
{
	return zend_startup_module(&EXT_GLOBAL(module_entry));
}

void EXT_GLOBAL(zend_shutdown)(zend_extension *extension)
{
	/* Do nothing. */
}

int EXT_GLOBAL(api_no_check)(int api_no) 
{
  return SUCCESS;
}

#if 0
static size_t count_catch_blocks (zend_op_array *op_array) 
{
  size_t count = 0;
  opline = op_array->opcodes+2;
  end = opline + op_array->last;
  while (opline < end) {
	if (opline[-2]==42 && opline[-1]==109 && opline[0]==107) {
	  count++;
	}
  }
  return count;
}

static void grow_op_array  (zend_op_array *op_array, size_t size)
{
  op_array->size = size;
  op_array->opcodes = 
	erealloc(op_array->opcodes, (op_array->size)*sizeof(zend_op));
}

static void patch_addresses (zend_op_array *op_array, size_t pos)
{
  opline = op_array->opcodes + pos;
  end = opline + op_array->last;
  while (opline < end) {
	switch (opline->opcode) {
	case ZEND_JMP:
	  opline->op1.u.jmp_addr += 1;
	  break;
	case ZEND_JMPZ:
	case ZEND_JMPNZ:
	case ZEND_JMPZ_EX:
	case ZEND_JMPNZ_EX:
	  opline->op2.u.jmp_addr += 1;
	  break;
	  }
  }
}
static void patch_position (zend_op_array *op_array, size_t pos)
{
  memmove (op_array+pos+1, op_array+pos, op_array->last-pos);
  op_array[pos]=ZEND_NOP;
  patch_addresses(op_array, ++pos);
}

static void patch_op_array (zend_op_array *op_array)
{
  opline = op_array->opcodes+2;
  end = opline + op_array->last;
  while (opline < end) {
	if (opline[-2]==42 && opline[-1]==109 && opline[0]==107) {
	  patch_position (op_array, opline-op_array);
	  opline++;
	}
  }
}
#endif
/** 
 * Modify the oparray to gain speed. 
 *
 * The PHP/Java Bridge protocol supports an asynchronous protocol mode
 * which allows the front- and back end to run in parallel. This mode
 * is 20 times faster than the default protocol mode, but, at certain
 * points the state must be synchronized.
 *
 * The following code inserts the synchronization points. Since it
 * depends on the way the language scanner works, this code is
 * currently not portable.
 */
void EXT_GLOBAL(op_array_handler)(zend_op_array *op_array) 
{
#if 0
  patch_op_array();
#endif
}

#ifndef ZEND_EXT_API
#define ZEND_EXT_API    /**/
#endif
ZEND_EXTENSION();

zend_extension zend_extension_entry = {
  EXT_NAME(),
  BRIDGE_VERSION,
  "The PHP/Java Bridge authors",
  "(C) 2003-2007 by the authors",
  "http://php-java-bridge.sf.net",
  EXT_GLOBAL(zend_startup),
  EXT_GLOBAL(zend_shutdown),
  NULL,           /* activate_func_t */
  NULL,           /* deactivate_func_t */
  NULL,           /* message_handler_func_t */
  EXT_GLOBAL(op_array_handler),           /* op_array_handler_func_t */
  NULL,           /* statement_handler_func_t */
  NULL,           /* fcall_begin_handler_func_t */
  NULL,           /* fcall_end_handler_func_t */
  NULL,           /* op_array_ctor_func_t */
  NULL,           /* op_array_dtor_func_t */
  NULL,
  COMPAT_ZEND_EXTENSION_PROPERTIES
};

#endif	/* >= PHP5 */

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
