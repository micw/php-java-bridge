/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * This file contains the API implementation.

  Copyright (C) 2006 Jost Boekemeier

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

#include "api.h"

/* return zval_null and don't try again using a new connection */
#define JAVA_RETURN_NULL() { ZVAL_NULL(return_value); return 0; }

static short last_exception_get(proxyenv *jenv, zval**return_value)
{
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  return (*jenv)->writeInvokeEnd(jenv);
}
short EXT_GLOBAL(last_exception_get)(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT_WITH_RETVAL(0);
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): last_exception_get() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  return last_exception_get(jenv, &return_value);
}
static short last_exception_clear(proxyenv*jenv, zval**return_value) {
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  (*jenv)->writeObject(jenv, 0);
  return (*jenv)->writeInvokeEnd(jenv);
}
short EXT_GLOBAL(last_exception_clear)(INTERNAL_FUNCTION_PARAMETERS) {
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT_WITH_RETVAL(0);
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}

  return last_exception_clear(jenv, &return_value);
}

short EXT_GLOBAL(set_file_encoding)(INTERNAL_FUNCTION_PARAMETERS) {
  zval **enc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &enc) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  convert_to_string_ex(enc);

  (*jenv)->writeInvokeBegin(jenv, 0, "setFileEncoding", 0, 'I', return_value);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(enc), Z_STRLEN_PP(enc));
  return (*jenv)->writeInvokeEnd(jenv);
}

short EXT_GLOBAL(require)(INTERNAL_FUNCTION_PARAMETERS) {
  static const char ext_dir[] = "extension_dir";
  char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
  zval **path;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  convert_to_string_ex(path);

#if EXTENSION == JAVA
  (*jenv)->writeInvokeBegin(jenv, 0, "updateJarLibraryPath", 0, 'I', return_value);
#else
  (*jenv)->writeInvokeBegin(jenv, 0, "updateLibraryPath", 0, 'I', return_value);
#endif
  (*jenv)->writeString(jenv, Z_STRVAL_PP(path), Z_STRLEN_PP(path));
  (*jenv)->writeString(jenv, ext, strlen(ext));
  return (*jenv)->writeInvokeEnd(jenv);
}

short EXT_GLOBAL(instanceof) (INTERNAL_FUNCTION_PARAMETERS) {
  zval **pobj, **pclass;
  long obj, class;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=2 || zend_get_parameters_ex(2, &pobj, &pclass) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  convert_to_object_ex(pobj);
  convert_to_object_ex(pclass);

  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
    zend_error(E_ERROR, "Argument #1 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
    return 0;
  }

  class = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pclass, &class TSRMLS_CC);
  if(!class) {
    zend_error(E_ERROR, "Argument #2 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
    return 0;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "InstanceOf", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeObject(jenv, class);
  
  return (*jenv)->writeInvokeEnd(jenv);
}


static long session_get_default_lifetime() {
  static const char session_max_lifetime[]="session.gc_maxlifetime";
  long l = zend_ini_long((char*)session_max_lifetime, sizeof(session_max_lifetime), 0);
  return l==0?1440:l;
}
short EXT_GLOBAL(session)(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **session=0, **is_new=0;
  int argc=ZEND_NUM_ARGS();
  char *current_ctx;
  
  if (argc>2 || zend_get_parameters_ex(argc, &session, &is_new) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) JAVA_RETURN_NULL();
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): get_session() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  current_ctx = (*jenv)->current_servlet_ctx;
  assert(EXT_GLOBAL(cfg)->is_cgi_servlet && current_ctx ||!EXT_GLOBAL(cfg)->is_cgi_servlet);
  /* create a new connection to the
     back-end if java_session() is not
     the first statement in a script */
  EXT_GLOBAL(check_session) (jenv TSRMLS_CC);

  (*jenv)->writeInvokeBegin(jenv, 0, "getSession", 0, 'I', return_value);
  /* call getSession(String id, ...), if necessary */
  if(current_ctx && current_ctx != (*jenv)->servlet_ctx)
    (*jenv)->writeString(jenv, current_ctx, strlen(current_ctx));

  if(argc>0 && Z_TYPE_PP(session)!=IS_NULL) {
    convert_to_string_ex(session);
    (*jenv)->writeString(jenv, Z_STRVAL_PP(session), Z_STRLEN_PP(session)); 
  } else {
    (*jenv)->writeObject(jenv, 0);
  }
  (*jenv)->writeBoolean(jenv, (argc<2||Z_TYPE_PP(is_new)==IS_NULL)?0:Z_BVAL_PP(is_new)); 

  (*jenv)->writeLong(jenv, session_get_default_lifetime()); // session.gc_maxlifetime

  if(!(*jenv)->writeInvokeEnd(jenv)) return 0;
  (*jenv)->backend_has_session_proxy=1;
  return 1;
}

short EXT_GLOBAL(context)(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  int argc=ZEND_NUM_ARGS();
  char *current_ctx = 0;
  
  if (argc!=0)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) JAVA_RETURN_NULL();
  current_ctx = (*jenv)->current_servlet_ctx;
  assert(EXT_GLOBAL(cfg)->is_cgi_servlet && current_ctx ||!EXT_GLOBAL(cfg)->is_cgi_servlet);
  (*jenv)->writeInvokeBegin(jenv, 0, "getContext", 0, 'I', return_value);
  /* call getContext(String id, ...), if necessary */
  if(current_ctx && current_ctx != (*jenv)->servlet_ctx)
    (*jenv)->writeString(jenv, current_ctx, strlen(current_ctx));
  return (*jenv)->writeInvokeEnd(jenv);
}

short EXT_GLOBAL(reset)(INTERNAL_FUNCTION_PARAMETERS) 
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT_WITH_RETVAL(0);

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) JAVA_RETURN_NULL();

  (*jenv)->writeInvokeBegin(jenv, 0, "reset", 0, 'I', return_value);
  if(!(*jenv)->writeInvokeEnd(jenv)) return 0;
  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Your script has called the privileged procedure \""/**/EXT_NAME()/**/"_reset()\" which resets the "/**/EXT_NAME()/**/" back-end to its initial state. Therefore all "/**/EXT_NAME()/**/" caches are gone.", 18);
  return 1;
}

static const char end[] = "endDocument";
short EXT_GLOBAL(begin_document)(INTERNAL_FUNCTION_PARAMETERS)
{
  static const char begin[] = "beginDocument";
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT_WITH_RETVAL(0);
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): begin_document() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  (*jenv)->writeInvokeBegin(jenv, 0, (char*)begin, sizeof(begin)-1, 'I', return_value);
  if(!(*jenv)->writeInvokeEnd(jenv)) return 0;
  if(!EXT_GLOBAL(begin_async)(jenv)) {
	(*jenv)->writeInvokeBegin(jenv, 0, (char*)end, sizeof(end)-1, 'I', return_value);
	(*jenv)->writeInvokeEnd(jenv);
	EXT_GLOBAL(sys_error)("could not open async buffer",94);
  }
  return 1;
}

short EXT_GLOBAL(end_document)(INTERNAL_FUNCTION_PARAMETERS) {
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT_WITH_RETVAL(0);
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}
  if((*jenv)->handle!=(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): end_document() invalid when not in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  (*jenv)->writeInvokeBegin(jenv, 0, (char*)end, sizeof(end)-1, 'I', return_value);
  EXT_GLOBAL(end_async)(jenv);
  return (*jenv)->writeInvokeEnd(jenv);
}

short EXT_GLOBAL(values)(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **pobj;
  long obj;

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) JAVA_RETURN_NULL();
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): values() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  obj = 0;
  if(Z_TYPE_PP(pobj) == IS_OBJECT) {
	EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  }

  if(!obj) {
	*return_value = **pobj;
	zval_copy_ctor(return_value);
	return 0;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "getValues", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  return (*jenv)->writeInvokeEnd(jenv);
}

static const char warn_session[] = 
  "the session module's session_write_close() tried to write garbage, aborted. \
-- If \"session_write_close();\" at the end of \
your script fixes this problem, please report this bug \
to the PHP release team.";
static const char identity[] = "serialID";
short EXT_GLOBAL(serialize)(INTERNAL_FUNCTION_PARAMETERS)
{
  long obj;
  zval *handle, *id;

  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {
    php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized. %s", warn_session);
    JAVA_RETURN_NULL();
  }
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): serialize() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  if(!obj) {
    /* set a breakpoint in java_bridge.c destroy_object, in rshutdown
       and get_jobject_from_object */
    php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized. %s", warn_session);
    JAVA_RETURN_NULL();
  }

  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  (*jenv)->writeInvokeBegin(jenv, 0, "serialize", 0, 'I', handle);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeLong(jenv, session_get_default_lifetime()); // session.gc_maxlifetime
  if(!(*jenv)->writeInvokeEnd(jenv)) { 
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not write session: Connection to back-end lost.",54);
	zval_ptr_dtor(&handle);
	JAVA_RETURN_NULL();
  }
  zend_hash_update(Z_OBJPROP_P(getThis()), (char*)identity, sizeof identity, &handle, sizeof(pval *), NULL);

  /* Return the field that should be serialized ("serialID") */
  array_init(return_value);
  INIT_PZVAL(return_value);

  MAKE_STD_ZVAL(id);
  Z_TYPE_P(id)=IS_STRING;
  Z_STRLEN_P(id)=sizeof(identity)-1;
  Z_STRVAL_P(id)=estrdup(identity);
  zend_hash_index_update(Z_ARRVAL_P(return_value), 0, &id, sizeof(pval*), NULL);
  return 1;
}
short EXT_GLOBAL(deserialize)(INTERNAL_FUNCTION_PARAMETERS)
{
  zval *handle, **id;
  int err;

  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {
    php_error(E_ERROR, EXT_NAME()/**/" cannot be de-serialized. %s", warn_session);
  }
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
    php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): deserialize() invalid while in stream mode", 21);
    JAVA_RETURN_NULL();
  }

  err = zend_hash_find(Z_OBJPROP_P(getThis()), (char*)identity, sizeof identity, (void**)&id);
  if(FAILURE==err) {
    /* set a breakpoint in java_bridge.c destroy_object, in rshutdown
       and get_jobject_from_object */
    php_error(E_ERROR, EXT_NAME()/**/" cannot be deserialized. %s", warn_session);
    JAVA_RETURN_NULL();
  }
  
  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  if(Z_TYPE_PP(id) == IS_STRING) {
	(*jenv)->writeInvokeBegin(jenv, 0, "deserialize", 0, 'I', handle);
	(*jenv)->writeString(jenv, Z_STRVAL_PP(id), Z_STRLEN_PP(id));
	(*jenv)->writeLong(jenv, session_get_default_lifetime()); // use session.gc_maxlifetime
	if(!(*jenv)->writeInvokeEnd(jenv)) {
	  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not read session: Connection to back-end lost.",53);
	  ZVAL_NULL(handle);
	}
  }

  if(Z_TYPE_P(handle)!=IS_LONG) {
#ifndef ZEND_ENGINE_2
    php_error(E_WARNING, EXT_NAME()/**/" cannot be deserialized, session expired.");
#endif
    ZVAL_NULL(getThis());
  }	else {
#ifndef ZEND_ENGINE_2
    zend_hash_index_update(Z_OBJPROP_P(getThis()), 0, &handle, sizeof(pval *), NULL);
#else
    EXT_GLOBAL(store_jobject)(getThis(), Z_LVAL_P(handle) TSRMLS_CC);
#endif
  }
  
  RETVAL_NULL();
  return 1;
}

short EXT_GLOBAL(get_closure)(INTERNAL_FUNCTION_PARAMETERS)
{
  zstr string_key;
  ulong num_key;
  zval **pobj, **pfkt, **pclass, **val;
  long class = 0;
  int key_type;
  proxyenv *jenv;
  int argc = ZEND_NUM_ARGS();

  if (argc>3 || zend_get_parameters_ex(argc, &pobj, &pfkt, &pclass) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) JAVA_RETURN_NULL();


  if (argc>0 && *pobj && Z_TYPE_PP(pobj) == IS_OBJECT) {
    zval_add_ref(pobj);
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "makeClosure", 0, 'I', return_value);
  (*jenv)->writeLong(jenv, (argc==0||Z_TYPE_PP(pobj)==IS_NULL)?0:(long)*pobj);

  /* fname -> cname Map */
  if(argc>1) {
    if (Z_TYPE_PP(pfkt) == IS_ARRAY) {
      (*jenv)->writeCompositeBegin_h(jenv);
      zend_hash_internal_pointer_reset(Z_ARRVAL_PP(pfkt));
      while ((key_type = zend_hash_get_current_key(Z_ARRVAL_PP(pfkt), &string_key, &num_key, 1)) != HASH_KEY_NON_EXISTANT) {
	if ((zend_hash_get_current_data(Z_ARRVAL_PP(pfkt), (void**)&val) == SUCCESS)) {
	  if(Z_TYPE_PP(val) == IS_STRING && key_type==HASH_KEY_IS_STRING) { 
	    size_t len = strlen(ZSTR_S(string_key));
	    (*jenv)->writePairBegin_s(jenv, ZSTR_S(string_key), len);
	    (*jenv)->writeString(jenv, Z_STRVAL_PP(val), Z_STRLEN_PP(val));
	    (*jenv)->writePairEnd(jenv);
	  } else {
	    zend_error(E_ERROR, "Argument #2 for %s() must be null, a string, or a map of java => php function names.", get_active_function_name(TSRMLS_C));
	  }
	}
	zend_hash_move_forward(Z_ARRVAL_PP(pfkt));
      }
      (*jenv)->writeCompositeEnd(jenv);
    } else if (Z_TYPE_PP(pfkt) == IS_STRING) {
      (*jenv)->writeString(jenv, Z_STRVAL_PP(pfkt), Z_STRLEN_PP(pfkt));
    } else {
      (*jenv)->writeCompositeBegin_h(jenv);
      (*jenv)->writeCompositeEnd(jenv);
    }
  }

  /* interfaces */
  if(argc>2) {
    (*jenv)->writeCompositeBegin_a(jenv);
    if(Z_TYPE_PP(pclass) == IS_ARRAY) {
      zend_hash_internal_pointer_reset(Z_ARRVAL_PP(pclass));
      while ((key_type = zend_hash_get_current_data(Z_ARRVAL_PP(pclass), (void**)&val)) == SUCCESS) {
	EXT_GLOBAL(get_jobject_from_object)(*val, &class TSRMLS_CC);
	if(class) { 
	  (*jenv)->writePairBegin(jenv);
	  (*jenv)->writeObject(jenv, class);
	  (*jenv)->writePairEnd(jenv);
	} else {
	  zend_error(E_ERROR, "Argument #3 for %s() must be a "/**/EXT_NAME()/**/" interface or an array of interfaces.", get_active_function_name(TSRMLS_C));
	}
	zend_hash_move_forward(Z_ARRVAL_PP(pclass));
      }
    } else {
      EXT_GLOBAL(get_jobject_from_object)(*pclass, &class TSRMLS_CC);
      if(class) { 
	(*jenv)->writePairBegin(jenv);
	(*jenv)->writeObject(jenv, class);
	(*jenv)->writePairEnd(jenv);
      } else {
	zend_error(E_ERROR, "Argument #3 for %s() must be a "/**/EXT_NAME()/**/" interface or an array of interfaces.", get_active_function_name(TSRMLS_C));
      }
    }
    (*jenv)->writeCompositeEnd(jenv);
  }
  return (*jenv)->writeInvokeEnd(jenv);
}

short EXT_GLOBAL(inspect) (INTERNAL_FUNCTION_PARAMETERS) 
{
  zval **pobj;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {JAVA_RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE)
    WRONG_PARAM_COUNT_WITH_RETVAL(0);

  convert_to_object_ex(pobj);
  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
    zend_error(E_ERROR, "Argument for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
    return 0;
  }
  (*jenv)->writeInvokeBegin(jenv, 0, "inspect", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);

  return ((*jenv)->writeInvokeEnd(jenv));
}

short EXT_GLOBAL(construct_class)(INTERNAL_FUNCTION_PARAMETERS)
{
  zval ***argv;
  int argc = ZEND_NUM_ARGS();
  short rc;
  
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  if (zend_get_parameters_array_ex(argc, argv) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }
  
  if(argc<1 || Z_TYPE_PP(argv[0])!=IS_STRING) WRONG_PARAM_COUNT_WITH_RETVAL(0);
  
  rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
					 EXT_NAME(), CONSTRUCTOR, 0, 
					 getThis(),
					 argc, argv);
  efree(argv);
  return rc;
}

short EXT_GLOBAL(call)(INTERNAL_FUNCTION_PARAMETERS) {
  zval ***xargv, ***argv;
  int i = 0, xargc, argc = ZEND_NUM_ARGS();
  HashPosition pos;
  zval **param;
  short rc;

  argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  if (zend_get_parameters_array_ex(argc, argv) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }

  /* function arguments in arg#2 */
  xargc = zend_hash_num_elements(Z_ARRVAL_PP(argv[1]));
  xargv = (zval***) safe_emalloc(sizeof(zval **), xargc, 0);
  for (zend_hash_internal_pointer_reset_ex(Z_ARRVAL_PP(argv[1]), &pos);
       zend_hash_get_current_data_ex(Z_ARRVAL_PP(argv[1]), (void **) &param, &pos) == SUCCESS;
       zend_hash_move_forward_ex(Z_ARRVAL_PP(argv[1]), &pos)) {
    /*zval_add_ref(param);*/
    xargv[i++] = param;
  }

  rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
					 Z_STRVAL_P(*argv[0]), CONSTRUCTOR_NONE,
					 0,
					 getThis(),
					 xargc, xargv);
  efree(argv);
  efree(xargv);
  return rc;
}

short EXT_GLOBAL(toString)(INTERNAL_FUNCTION_PARAMETERS) {
  zval *trace = 0;
  long result = 0;
  short rc = 0;
  
  if(Z_TYPE_P(getThis()) == IS_OBJECT) {
    EXT_GLOBAL(get_jobject_from_object)(getThis(), &result TSRMLS_CC);
  }
  if(result) {
    proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
    if(!jenv) {JAVA_RETURN_NULL();}
    if((*jenv)->handle==(*jenv)->async_ctx.handle_request){/* async protocol */
      php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): __tostring() invalid while in stream mode", 21);
      JAVA_RETURN_NULL();
    }

    (*jenv)->writeInvokeBegin(jenv, 0, "ObjectToString", 0, 'I', return_value);
    (*jenv)->writeObject(jenv, result);
#ifdef ZEND_ENGINE_2
	if (instanceof_function(Z_OBJCE_P(getThis()), EXT_GLOBAL(exception_class_entry) TSRMLS_CC)) {
	  zval fname;
	  ZVAL_STRINGL(&fname, "gettraceasstring", sizeof("gettraceasstring")-1, 0);
	  call_user_function_ex(0, &getThis(), &fname, &trace, 0, 0, 1, 0 TSRMLS_CC);
	  if(trace) 
		(*jenv)->writeString(jenv, Z_STRVAL_P(trace), Z_STRLEN_P(trace));
	}
#endif
    rc = (*jenv)->writeInvokeEnd(jenv);

  } else {
    rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
					   "tostring", CONSTRUCTOR_NONE, 
					   0, getThis(), 0, NULL);
  }
  if(trace) zval_ptr_dtor(&trace);
  return rc;
}
short EXT_GLOBAL(set)(INTERNAL_FUNCTION_PARAMETERS)
{
  zval ***argv;
  int argc = ZEND_NUM_ARGS();
  short rc;
  
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  if (zend_get_parameters_array_ex(argc, argv) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }
  
  rc = EXT_GLOBAL(set_property_handler)(Z_STRVAL_P(*argv[0]), getThis(), *argv[1], return_value);
  
  efree(argv);
  return rc;
}

short EXT_GLOBAL(get)(INTERNAL_FUNCTION_PARAMETERS) 
{
  zval ***argv;
  int argc = ZEND_NUM_ARGS();
  short rc;

  argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  if (zend_get_parameters_array_ex(argc, argv) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }
  
  rc = EXT_GLOBAL(get_property_handler)(Z_STRVAL_P(*argv[0]), getThis(), return_value);
  efree(argv);
  return rc;
}

short EXT_GLOBAL(offsetExists) (INTERNAL_FUNCTION_PARAMETERS) 
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  zval ***argv;
  zval *thiz = getThis();
  int argc;
  short rc;
  
  if(!jenv) {JAVA_RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc+1, 0);
  if (zend_get_parameters_array_ex(argc, argv+1) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }
  argv[0]=&thiz;
  rc = EXT_GLOBAL(invoke)("offsetExists", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
  return rc;
}

short EXT_GLOBAL(offsetGet)(INTERNAL_FUNCTION_PARAMETERS) 
{
  zval ***argv;
  zval *thiz = getThis();
  int argc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  short rc;
  if(!jenv) {JAVA_RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc+1, 0);
  if (zend_get_parameters_array_ex(argc, argv+1) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }

  argv[0]=&thiz;
  rc = EXT_GLOBAL(invoke)("offsetGet", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
  return rc;
}

short EXT_GLOBAL(offsetSet) (INTERNAL_FUNCTION_PARAMETERS)
{
  zval ***argv;
  zval *thiz = getThis();
  int argc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  short rc;
  if(!jenv) {JAVA_RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc+1, 0);
  if (zend_get_parameters_array_ex(argc, argv+1) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }

  argv[0]=&thiz;
  rc = EXT_GLOBAL(invoke)("offsetSet", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
  return rc;
}

short EXT_GLOBAL(offsetUnset)(INTERNAL_FUNCTION_PARAMETERS) 
{
  zval ***argv;
  zval *thiz = getThis();
  int argc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  short rc;
  if(!jenv) {JAVA_RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval ***) safe_emalloc(sizeof(zval **), argc+1, 0);
  if (zend_get_parameters_array_ex(argc, argv+1) == FAILURE) {
    php_error(E_ERROR, "Couldn't fetch arguments into array.");
    JAVA_RETURN_NULL();
  }

  argv[0]=&thiz;
  rc = EXT_GLOBAL(invoke)("offsetUnset", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
  return rc;
}

static int get_parameters_array(zval*arr, zval***argument_array TSRMLS_DC)
{
  zval **param_ptr;
  zend_hash_internal_pointer_reset(Z_ARRVAL_P(arr));
  while(zend_hash_get_current_data(Z_ARRVAL_P(arr), (void**)&param_ptr) == SUCCESS) {
	*(argument_array++) = param_ptr;
	zend_hash_move_forward(Z_ARRVAL_P(arr));
  }
}
static short construct_array(zval**args, INTERNAL_FUNCTION_PARAMETERS) {
  short rc;
  int argc = zend_hash_num_elements(Z_ARRVAL_PP(args));
  zval ***argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  get_parameters_array(*args, argv TSRMLS_CC);
  if(argc<1 || Z_TYPE_PP(argv[0])!=IS_STRING) {
	efree(argv);
	WRONG_PARAM_COUNT_WITH_RETVAL(0);
  }
  rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
										 EXT_NAME(), CONSTRUCTOR, 1,
										 getThis(),
										 argc, argv);
  efree(argv);
  return rc;
}
static short construct_no_arg(zval**arg, INTERNAL_FUNCTION_PARAMETERS) {
  short rc;
  int argc = 1;
  zval ***argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  argv[0] = arg;
  if(Z_TYPE_PP(argv[0])!=IS_STRING) {
	efree(argv);
	WRONG_PARAM_COUNT_WITH_RETVAL(0);
  }
  rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
										 EXT_NAME(), CONSTRUCTOR, 1,
										 getThis(),
										 argc, argv);
  efree(argv);
  return rc;
}
static short construct(int argc, INTERNAL_FUNCTION_PARAMETERS) {
  short rc;
  zval ***argv = (zval ***) safe_emalloc(sizeof(zval **), argc, 0);
  if (zend_get_parameters_array_ex(argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	JAVA_RETURN_NULL();
  }
  if(argc<1 || Z_TYPE_PP(argv[0])!=IS_STRING) {
	efree(argv);
	WRONG_PARAM_COUNT_WITH_RETVAL(0);
  }
  rc = EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
										 EXT_NAME(), CONSTRUCTOR, 1,
										 getThis(),
										 argc, argv);
  efree(argv);
  return rc;
}
short EXT_GLOBAL(construct)(INTERNAL_FUNCTION_PARAMETERS)
{
  zval **args;
  int argc = ZEND_NUM_ARGS();
  short rc = 0;

  if(argc==1) {
	if(zend_get_parameters_ex(1, &args) == FAILURE)
	  WRONG_PARAM_COUNT_WITH_RETVAL(0);

	rc = (Z_TYPE_PP(args) == IS_ARRAY) ?
	  construct_array(args, INTERNAL_FUNCTION_PARAM_PASSTHRU) :
	  construct_no_arg(args, INTERNAL_FUNCTION_PARAM_PASSTHRU);
  } else {
	rc = construct(argc, INTERNAL_FUNCTION_PARAM_PASSTHRU);
  }
  
  return rc;
}
