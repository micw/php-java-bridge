# -*- mode: Makefile; -*-

$(phplibdir)/libnatcJavaBridge.la:
	cd $(srcdir)/server; $(MAKE) CFLAGS="$(CFLAGS_CLEAN)" GCJFLAGS="$(GCJFLAGS) `echo $(CFLAGS_CLEAN)|sed 's/-D[^ ]*//g'`" install
