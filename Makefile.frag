# -*- mode: Makefile; -*-

$(srcdir)/init_cfg.o: $(srcdir)/init_cfg.c
	$(LIBTOOL) --mode=compile $(CC) -c $(COMMON_FLAGS) ${CFLAGS} -DCFG_CLASSPATH="\"${EXTENSION_DIR}\"" -DCFG_LD_LIBRARY_PATH="\"${EXTENSION_DIR}\"" -DCFG_JAVA="\"${PHP_JAVA_BIN}\"" -DCFG_JAVA_HOME="\"${PHP_JAVA}\"" -DBRIDGE_VERSION=\"`cat VERSION`\" $(srcdir)/init_cfg.c -o $@

$(srcdir)/server/natcJavaBridge.c : $(phplibdir)/JavaBridge.class

$(phplibdir)/JavaBridge.class: $(srcdir)/server/JavaBridge.java
	(cd $(srcdir)/server; ${PHP_JAVA}/bin/javac JavaBridge.java && cp JavaBridge*.class `dirname $@`)

# no JVM: compile bridge into a binary
$(srcdir)/server/natcJavaBridge.o: 
	libtool --mode=compile gcc -c $(COMMON_FLAGS) ${CFLAGS} -o $@ $(srcdir)/server/natcJavaBridge.c

$(srcdir)/server/JavaBridge.o: $(srcdir)/server/JavaBridge.java
	libtool --mode=compile gcj -c -fjni -o $@ $(srcdir)/server/JavaBridge.java

all: $(phplibdir)/java

$(phplibdir)/java:  $(phplibdir)/libnatcJavaBridge.la
	if test "$(PHP_JAVA)" = "yes"; then gcj -Wl,--rpath -Wl,$(INSTALL_ROOT)$(EXTENSION_DIR)/ -L$(phplibdir) -lnatcJavaBridge -o $@ $(srcdir)/server/java.c; fi
