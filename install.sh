#!/bin/sh

#set -x
v=""
if test "X$1" = "X--verbose" || test "X$1" = "X-v" ; then
v="-v"
fi

echo "<?php phpinfo();?>" | php 2>/dev/null >/tmp/phpinfo.$$
ini=`fgrep "Scan this dir for additional" /tmp/phpinfo.$$ | head -1 |
sed 's/<[TtRr][/a-z ="0-9]*>/ => /g' | 
sed 's/<[/a-z ="0-9]*>//g' | 
sed 's/^.*=> //'`
rm -f /tmp/phpinfo.$$

# install generic ini
make install
cp $v java.ini $ini

# j2ee/servlet
if test -f modules/JavaBridge.war; then
    echo ""
    echo "Do you want to install the Servlet/J2EE backend (recommended)?";
    echo -n "install j2ee backend (yes/no): "; read j2ee;
    if test "X$j2ee" != "Xno"; then
      webapps="`locate /webapps | head -1`"
      echo ""
      echo "Enter the location of the autodeploy folder.";
      echo -n "autodeploy ($webapps): "; read $webapps2;
      if test X$webapps2 != X; then webapps=$webapps2; fi
      cp $v modules/JavaBridge.war $webapps;
      echo "installed in $webapps"
      cp $v java-servlet.ini $ini
    fi
fi

# standalone
if test -f modules/JavaBridge.jar && test "X$j2ee" = "Xno"; then
    echo ""
    echo "Do you want to install the standalone backend (deprecated)?";
    echo -n "install standalone backend (yes/no): "; read standalone;
    if test "X$standalone" == "Xyes"; then
	cp $v php-java-bridge /usr/sbin
	chmod +x /usr/sbin/php-java-bridge
	cp $v php-java-bridge.service /etc/init.d/php-java-bridge
	/sbin/chkconfig --add php-java-bridge
	/sbin/chkconfig php-java-bridge on
	echo "installed in /usr/sbin"
	cp $v java-standalone.ini $ini
    fi
fi

# devel
if test -f modules/php-script.jar; then
    echo ""
    echo "Do you want to install the development files (recommended)?";
    echo -n "install development files (yes/no): "; read devel;
    if test "X$devel" != "Xno"; then
	cp $v modules/JavaBridge.jar \
	      modules/php-script.jar \
	      modules/script-api.jar /usr/share/java

	mkdir -p /usr/java/packages/lib/ext 2>/dev/null

	rm -f /usr/java/packages/lib/ext/JavaBridge.jar \
	      /usr/java/packages/lib/ext/php-script.jar

	ln $v -s /usr/share/java/JavaBridge.jar \
	         /usr/share/java/php-script.jar /usr/java/packages/lib/ext

	echo "installed in /usr/share/java"
	echo "Type /usr/java/jdk1.6.0/bin/jrunscript -l php-interactive"
	echo "to run php from java."
      fi
fi

echo ""
echo "PHP/Java Bridge installed"
if test -d /etc/selinux && /usr/sbin/selinuxenabled; then
echo "You are running a SELinx system. Please install the policy sources"
echo "or install the files from the RPM distribution download."
echo "Please see the README document for details".
fi
