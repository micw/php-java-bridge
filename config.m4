sinclude(tests.m4/function_checks.m4)
sinclude(tests.m4/threads.m4)
sinclude(tests.m4/java_check_broken_stdio_buffering.m4)
sinclude(tests.m4/java_check_struct_ucred.m4)
sinclude(tests.m4/java_check_abstract_namespace.m4)
sinclude(tests.m4/java_check_broken_gcc_installation.m4)

PHP_ARG_WITH(java, for java support,
[  --with-java[=JAVA_HOME]        Include java support])


if test "$PHP_JAVA" != "no"; then
       JAVA_FUNCTION_CHECKS
       PTHREADS_CHECK
       PTHREADS_ASSIGN_VARS
       PTHREADS_FLAGS
       JAVA_CHECK_BROKEN_STDIO_BUFFERING
       JAVA_CHECK_BROKEN_GCC_INSTALLATION
       JAVA_CHECK_ABSTRACT_NAMESPACE
       JAVA_CHECK_STRUCT_UCRED
       if test "$have_broken_gcc_installation" = "yes"; then
         AC_MSG_WARN([YOUR GCC INSTALLATION IS BROKEN. It tries to link with the same library for -m32 and -m64 builds. This may result in a "wrong ELF class" error at runtime. Although you can work around this bug at runtime by changing the LD_LIBRARY_PATH, we recommend to re-install the gcc compiler before you continue to install the PHP/Java Bridge.])
	  sleep 30
       fi

# find includes eg. -I/opt/jdk1.4/include -I/opt/jdk1.4/include/linux
        if test "$PHP_JAVA" != "yes"; then
	 PHP_EVAL_INCLINE(`for i in \`find $PHP_JAVA/include -follow -type d -print\`; do echo -n "-I$i "; done`)
	 COND_GCJ=0
	else
	 COND_GCJ=1
	fi

# create java.so, compile with -DEXTENSION_DIR="\"$(EXTENSION_DIR)\""
	PHP_NEW_EXTENSION(java, java.c java_bridge.c client.c proxyenv.c bind.c init_cfg.c ,$ext_shared,,[-DEXTENSION_DIR=\"\\\\\"\\\$(EXTENSION_DIR)\\\\\"\"])
# create init_cfg.c from the template (same as AC_CONFIG_FILES)
	BRIDGE_VERSION="`cat $ext_builddir/VERSION`"
	PHP_JAVA_BIN="${PHP_JAVA}/bin/java"
	sed "s*@PHP_JAVA@*${PHP_JAVA}*
	     s*@COND_GCJ@*${COND_GCJ}*
             s*@PHP_JAVA_BIN@*${PHP_JAVA_BIN}*
             s*@BRIDGE_VERSION@*${BRIDGE_VERSION}*" \
          <$ext_builddir/init_cfg.c.in >$ext_builddir/init_cfg.c

# bootstrap the server's configure script
	AC_CONFIG_SUBDIRS(server)
	sed "s*@EXTENSION_DIR@*${EXTENSION_DIR}*
             s*@phplibdir@*`pwd`/modules*" \
          <$ext_builddir/server/configure.gnu.in >$ext_builddir/server/configure.gnu

# an artificial target so that the server/ part gets compiled
	PHP_ADD_MAKEFILE_FRAGMENT
	PHP_SUBST(JAVA_SHARED_LIBADD)
	PHP_MODULES="$PHP_MODULES \$(phplibdir)/libnatcJavaBridge.la"

fi
