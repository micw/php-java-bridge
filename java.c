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

/* wait, stat */
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
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

PHP_RINIT_FUNCTION(EXT) 
{
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
  if(EXT_GLOBAL(cfg)->socketname_set && (EXT_GLOBAL(cfg)->sockname)) {
	char *name = EXT_GLOBAL(cfg)->sockname;
	if(name[0]=='@' || name[0]=='/') { /* unix domain socket */
	  RETURN_STRING(name, 1);
	} else {					/* tcp socket */
	  RETURN_LONG(atoi(EXT_GLOBAL(cfg)->sockname));
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
PHP_INI_BEGIN()
  PHP_INI_ENTRY(EXT_NAME()/**/".servlet", NULL, PHP_INI_SYSTEM, OnIniServlet)
  PHP_INI_ENTRY(EXT_NAME()/**/".socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
  PHP_INI_ENTRY(EXT_NAME()/**/".hosts",   NULL, PHP_INI_SYSTEM, OnIniHosts)
  PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
  PHP_INI_END()

/* PREFORK calls this once. All childs receive cloned values. However,
   the WORKER MPM calls this for the master and for all childs */
  static void EXT_GLOBAL(alloc_globals_ctor)(EXT_GLOBAL_EX(zend_,globals,_) *EXT_GLOBAL(globals) TSRMLS_DC)
{
  memset (EXT_GLOBAL(globals), 0, sizeof*EXT_GLOBAL(globals));
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
  EXT_INIT_MODULE_GLOBALS(EXT, EXT_GLOBAL(alloc_globals_ctor), NULL);
  
  assert(!EXT_GLOBAL (cfg) );
  if(!EXT_GLOBAL (cfg) ) EXT_GLOBAL (cfg) = malloc(sizeof *EXT_GLOBAL (cfg) ); 
  if(!EXT_GLOBAL (cfg) ) exit(9);

  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	EXT_GLOBAL(init_cfg) (TSRMLS_C);
  } 

  // disable named pipes if the socket option is set
  if(!EXT_GLOBAL(option_set_by_user)(U_SOCKNAME, EXT_GLOBAL(ini_user))) {
	struct stat buf;
	if ((0==stat("/dev/shm", &buf)) && (S_ISDIR(buf.st_mode)))
	  REGISTER_STRING_CONSTANT(EXTU/**/"_PIPE_DIR", "/dev/shm", CONST_CS | CONST_PERSISTENT);
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
	else
	  REGISTER_STRING_CONSTANT(EXTU/**/"_SERVLET", "", CONST_CS | CONST_PERSISTENT);
  }
  if(EXT_GLOBAL(option_set_by_user)(U_LOGLEVEL, EXT_GLOBAL(ini_user)))
	REGISTER_LONG_CONSTANT(EXTU/**/"_LOG_LEVEL", atoi(EXT_GLOBAL(cfg)->logLevel), CONST_CS | CONST_PERSISTENT);
  REGISTER_LONG_CONSTANT(EXTU/**/"_PERSISTENT_SERVLET_CONNECTIONS", 1, CONST_CS | CONST_PERSISTENT);
  return SUCCESS;
}

/**
 * Displays the module info.
 */
PHP_MINFO_FUNCTION(EXT)
{
  php_info_print_table_start();
  php_info_print_table_row(2, EXT_NAME()/**/" support", "Enabled");
  php_info_print_table_row(2, EXT_NAME()/**/" bridge", EXT_GLOBAL(bridge_version));
  if(EXT_GLOBAL(cfg)->socketname_set && EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))
	php_info_print_table_row(2, EXT_NAME()/**/".socketname", EXT_GLOBAL(cfg)->sockname);
  else {
	if(EXT_GLOBAL(option_set_by_user) (U_HOSTS, EXT_GLOBAL(ini_user)))
	  php_info_print_table_row(2, EXT_NAME()/**/".hosts", EXT_GLOBAL(cfg)->hosts);
	if(EXT_GLOBAL(option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user)))
	  php_info_print_table_row(2, EXT_NAME()/**/".servlet", EXT_GLOBAL(cfg)->servlet);
  }
  if(EXT_GLOBAL(option_set_by_user) (U_LOGLEVEL, EXT_GLOBAL(ini_user)))
	php_info_print_table_row(2, EXT_NAME()/**/".log_level", EXT_GLOBAL(cfg)->logLevel);
  php_info_print_table_end();
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

  assert(EXT_GLOBAL (cfg));
  if(EXT_GLOBAL (cfg) ) { 
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

#ifndef ZEND_EXT_API
#define ZEND_EXT_API    /**/
#endif
ZEND_EXTENSION();

zend_extension zend_extension_entry = {
  EXT_NAME(),
  BRIDGE_VERSION,
  "The PHP/Java Bridge authors",
  "(C) 2003-2008 by the authors",
  "http://php-java-bridge.sf.net",
  EXT_GLOBAL(zend_startup),
  EXT_GLOBAL(zend_shutdown),
  NULL,           /* activate_func_t */
  NULL,           /* deactivate_func_t */
  NULL,           /* message_handler_func_t */
  NULL,           /* op_array_handler_func_t */
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
