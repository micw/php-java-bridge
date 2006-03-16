#!/bin/sh

#set -x
v=""
if test "X$1" = "X--verbose" || test "X$1" = "X-v" ; then
v="-v"
fi

php=`php-config --php-binary`
if test $?; then 
 for i in /usr/bin/php /usr/bin/php-cgi /usr/local/bin/php /usr/local/bin/php-cgi; do
  if test -f $i; then php=$i; break; fi
 done
fi

if test X$php = X; then echo "php not installed, bye."; exit 3; fi

echo "<?php phpinfo();?>" | ${php} 2>/dev/null >/tmp/phpinfo.$$
ini=`fgrep "Scan this dir for additional" /tmp/phpinfo.$$ | head -1 |
sed 's/<[TtRr][/a-z ="0-9]*>/ => /g' | 
sed 's/<[/a-z ="0-9]*>//g' | 
sed 's/^.*=> //'`
ext=`fgrep extension_dir /tmp/phpinfo.$$ | head -1 |
sed 's/<[TtRr][/a-z ="0-9]*>/ => /g' | 
sed 's/<[/a-z ="0-9]*>//g' | 
sed 's/^.*=> //'`
/bin/rm -f /tmp/phpinfo.$$

# install generic ini
make install >install.log || exit 1
mod_dir=`cat install.log | sed -n '/Installing shared extensions:/s///p' | awk '{print $1}'`
echo "installed in $mod_dir";

if test X$ini = X; then
echo ""
echo "This php installation does not have a config-file-scan-dir,"
echo "java.ini, java-standalone.ini and java-servlet.ini not installed."
echo "You must edit the php.ini yourself."
fi

if test -d "$ext"; then 
echo "Using extension_dir: $ext"
else
echo ""
echo "Warning: Your php installation is broken, the \"extension_dir\" does "
echo "not exist or is not a directory (see php.ini extension_dir setting)."
echo "Please correct this, for example type:"
echo "mkdir \"$ext\""
echo ""
fi

/bin/rm -f $ini 2>/dev/null
if test X$ini != X; then
 /bin/mkdir -p $ini
 /bin/cp $v java.ini $ini
fi

j2ee=no
# j2ee/servlet
/bin/rm $v -f ${ini}/java-servlet.ini 2>/dev/null
if test -f modules/JavaBridge.war; then
    echo ""
    echo "Do you want to install the Servlet/J2EE backend (recommended)?";
    echo -n "install j2ee backend (yes/no): "; read j2ee;
    if test "X$j2ee" != "Xno"; then
      webapps="`locate /webapps | fgrep tomcat | grep 'webapps$' | head -1`"
      echo ""
      echo "Enter the location of the autodeploy folder.";
      echo -n "autodeploy ($webapps): "; read webapps2;
      if test X$webapps2 != X; then webapps=$webapps2; fi
      /bin/cp $v modules/JavaBridge.war $webapps;
      echo "installed in $webapps"
      if test X$ini != X; then  /bin/cp $v java-servlet.ini $ini; fi
    fi
fi

# standalone
/bin/rm $v -f ${ini}/java-standalone.ini 2>/dev/null
if test -f modules/JavaBridge.jar && test "X$j2ee" = "Xno"; then
    echo ""
    echo "Do you want to install the standalone backend (deprecated)?";
    echo -n "install standalone backend (yes/no): "; read standalone;
    if test "X$standalone" = "Xyes"; then
	/bin/cp $v php-java-bridge /usr/sbin
	chmod +x /usr/sbin/php-java-bridge
	for i in /etc/init.d /etc/rc.d/init.d /etc/rc.d; do
	if test -d $i; then
	    /bin/cp $v php-java-bridge.service ${i}/php-java-bridge
	    chmod +x ${i}/php-java-bridge
	    break;
	fi
	done
	/sbin/chkconfig --add php-java-bridge
	/sbin/chkconfig php-java-bridge on
	echo "installed in /usr/sbin"
	if test X$ini != X; then 
	    jre="`locate /bin/java | grep 'java$' | head -1`"
	    echo ""
	    echo "Enter the location of the "java" jre binary.";
	    echo -n "java executable ($jre): "; read jre2;
	    if test X$jre2 != X; then jre=$jre2; fi
	    cat java-standalone.ini | sed 's|^;java\.java_home[\t =].*$|;java.java_home = |; s|^;java\.java[\t =].*$|java.java = '$jre'|' >${ini}/java-standalone.ini
	    echo "installed in $ini"
	    
	fi
    fi
fi

# devel
if test -f modules/php-script.jar; then
    echo ""
    echo "Do you want to install the development files (recommended)?";
    echo -n "install development files (yes/no): "; read devel;
    if test "X$devel" != "Xno"; then
	/bin/mkdir -p /usr/share/java 2>/dev/null

	/bin/cp $v modules/JavaBridge.jar \
	      modules/php-script.jar \
	      modules/script-api.jar /usr/share/java

	/bin/mkdir -p /usr/java/packages/lib/ext 2>/dev/null

	/bin/rm -f /usr/java/packages/lib/ext/JavaBridge.jar \
	      /usr/java/packages/lib/ext/php-script.jar 2>/dev/null

	/bin/ln $v -s /usr/share/java/JavaBridge.jar \
	         /usr/share/java/php-script.jar /usr/java/packages/lib/ext

	echo "installed in /usr/share/java"
	echo "Type (e.g.) /usr/java/jdk1.6.0/bin/jrunscript -l php-interactive"
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
exit 0
