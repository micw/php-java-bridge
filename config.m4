sinclude(tests.m4/function_checks.m4)
sinclude(tests.m4/threads.m4)
sinclude(tests.m4/java_check_broken_stdio_buffering.m4)
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
       if test "$have_broken_gcc_installation" = "yes"; then
         AC_MSG_WARN([YOUR GCC INSTALLATION IS BROKEN. It tries to link with the same library for -m32 and -m64 builds. This will result in a "wrong ELF class" error at runtime. Although you can work around this bug at runtime by changing the LD_LIBRARY_PATH, we recommend to re-install the gcc compiler before you continue to install the PHP/Java Bridge.])
	  sleep 30
       fi

# the JAVA_HOME directory
	PHP_SUBST(PHP_JAVA)
	PHP_JAVA_BIN="${PHP_JAVA}/bin/java"
	PHP_SUBST(PHP_JAVA_BIN)

# find includes eg. -I/opt/jdk1.4/include -I/opt/jdk1.4/include/linux
        if test "$PHP_JAVA" != "yes"; then
	 PHP_EVAL_INCLINE(`for i in \`find $PHP_JAVA/include -type d -print\`; do echo -n "-I$i "; done`)
        fi

# create java.so
	PHP_NEW_EXTENSION(java, java.c java_bridge.c client.c proxyenv.c bind.c init_cfg.o ,shared)
	PHP_ADD_MAKEFILE_FRAGMENT

# create libnatcJavaBridge.so
        if test "$PHP_JAVA" != "yes"; then
	 PHP_NEW_EXTENSION(libnatcJavaBridge,server/natcJavaBridge.c ,shared)
        else
	 PHP_JAVA="${EXTENSION_DIR}"
	 PHP_JAVA_BIN="${PHP_JAVA}/java"
	 PHP_NEW_EXTENSION(libnatcJavaBridge,server/natcJavaBridge.o server/JavaBridge.o ,shared)
        fi
	PHP_ADD_BUILD_DIR($ext_builddir/server)
fi

