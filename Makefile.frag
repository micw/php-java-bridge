# -*- mode: Makefile; -*-


$(phplibdir)/libnatcJavaBridge.la:
	cd $(srcdir)/server; sh autogen.sh && sh ./configure --with-java="$(PHP_JAVA_HOME)" --with-extdir="$(EXTENSION_DIR)" --libdir="$(phplibdir)" --datadir="$(phplibdir)" --bindir="$(phplibdir)" && $(MAKE) CFLAGS="$(CFLAGS_CLEAN)" GCJFLAGS="$(GCJFLAGS) `echo $(CFLAGS_CLEAN)|sed 's/-D[^ ]*//g'`" install
