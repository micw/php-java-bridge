# -*- mode: Makefile; -*-

install_policy:
	enforce=`getenforce`
	setenforce 0
	domains=`find /etc/selinux -type d -name "domains"`
	contexts=`find /etc/selinux -type d -name "file_contexts"`
	n=0;
	for i in $domains; do
	if test -f $i/program/apache.te; then 
	cp php-java-bridge.te $i/program; n=1
	(cd $i/..; make)
	fi
	done
	if test $n = 0; then 
	echo "error: Could not install php-java-bridge.te. Are the SELinux sources and apache installed?"
	setenforce $enforce
	exit 1
	fi

	n=0;
	for i in $contexts; do
	if test -f $i/program/apache.fc; then 
	cp php-java-bridge.fc $i/program; n=1
	(cd $i/..
	make file_contexts/file_contexts 
	touch /var/log/php-java-bridge.log
	setfiles -v file_contexts/file_contexts /usr/lib/php4/RunJavaBridge /var/log/php-java-bridge.log
	)
	fi
	done
	if test $n = 0; then 
	echo "error: Could not install php-java-bridge.fc. Are the SELinux sources and apache installed?"
	setenforce $enforce
	exit 1
	fi
	setenforce $enforce
	echo "SELinux policy files for php-java-bridge installed."
	echo "You must now load the new policy into the kernel."

$(phplibdir)/libnatcJavaBridge.la:
	cd $(srcdir)/server; $(MAKE) CFLAGS="$(CFLAGS_CLEAN)" GCJFLAGS="$(GCJFLAGS) `echo $(CFLAGS_CLEAN)|sed 's/-D[^ ]*//g'`" install
