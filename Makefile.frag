# -*- mode: Makefile; -*-

$(srcdir)/init_cfg.o: $(srcdir)/init_cfg.c
	$(LIBTOOL) --mode=compile $(CC) -c $(COMMON_FLAGS) ${CFLAGS} -DCFG_CLASSPATH="\"${EXTENSION_DIR}\"" -DCFG_LD_LIBRARY_PATH="\"${EXTENSION_DIR}\"" -DCFG_JAVA="\"${PHP_JAVA}/bin/java\"" -DCFG_JAVA_HOME="\"${PHP_JAVA}\"" -DBRIDGE_VERSION=\"`cat VERSION`\" $(srcdir)/init_cfg.c -o $@

$(srcdir)/server/natcJavaBridge.c : $(phplibdir)/JavaBridge.class

$(phplibdir)/JavaBridge.class: $(srcdir)/server/JavaBridge.java
	(cd $(srcdir)/server; ${PHP_JAVA}/bin/javac JavaBridge.java && cp JavaBridge.class $@)
