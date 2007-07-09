# -*- mode: Makefile; -*-

$(srcdir)/java_inc.c: $(srcdir)/server/META-INF/java/Java.inc
	echo -n 'char java_inc[]="' >$(srcdir)/java_inc.c
	cat $(srcdir)/server/META-INF/java/Java.inc | sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/&\\n\\/'  >>$(srcdir)/java_inc.c
	echo '";' >>$(srcdir)/java_inc.c
	echo '#include <stddef.h>' >>$(srcdir)/java_inc.c
	echo 'size_t java_inc_length() {return sizeof(java_inc);}' >>$(srcdir)/java_inc.c


$(srcdir)/server/META-INF/java/Java.inc:
	cat $(srcdir)/server/META-INF/java/JavaBridge.inc $(srcdir)/server/META-INF/java/Options.inc $(srcdir)/server/META-INF/java/Client.inc $(srcdir)/server/META-INF/java/GlobalRef.inc $(srcdir)/server/META-INF/java/JavaProxy.inc $(srcdir)/server/META-INF/java/NativeParser.inc $(srcdir)/server/META-INF/java/Parser.inc $(srcdir)/server/META-INF/java/Protocol.inc $(srcdir)/server/META-INF/java/SimpleParser.inc | sed -f $(srcdir)/server/extract.sed | sed '/\/\*/,/\*\//d' >$(srcdir)/server/META-INF/java/Java.inc


$(phplibdir)/stamp:
	cd $(srcdir)/server; $(MAKE) CFLAGS="$(CFLAGS_CLEAN)" GCJFLAGS="$(GCJFLAGS) `echo $(CFLAGS_CLEAN)|sed 's/-D[^ ]*//g'`" install
