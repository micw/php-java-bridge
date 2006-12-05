# -*- mode: Makefile; -*-
tmp_prefix:=$(prefix)
prefix = ${DESTDIR}$(tmp_prefix)
TMP_EXTENSION_DIR:=$(EXTENSION_DIR)
EXTENSION_DIR=${DESTDIR}$(TMP_EXTENSION_DIR)


$(phplibdir)/stamp:
	cd $(srcdir)/server; $(MAKE) CFLAGS="$(CFLAGS_CLEAN)" GCJFLAGS="$(GCJFLAGS) `echo $(CFLAGS_CLEAN)|sed 's/-D[^ ]*//g'`" install

