# -*- mode: Makefile; -*-


$(phplibdir)/libnatcJavaBridge.la:
	CFLAGS="$(CFLAGS_CLEAN)"; cd $(srcdir)/server; sh autogen.sh && sh ./configure --with-java="$(PHP_JAVA_HOME)" --with-extdir="$(EXTENSION_DIR)" --libdir="$(phplibdir)" --datadir="$(phplibdir)" --bindir="$(phplibdir)" && make install
