#!/bin/sh

rm -rf rm -rf [^C][^V][^S]* .??* *~
cvs -Q update -APd 
find . -print | xargs touch

set -x

version=`cat VERSION`
ln -s `pwd` php-java-bridge-${version}

# create archive
tar czhf php-java-bridge_${version}.tar.gz --exclude "php-java-bridge-${version}/php-java-bridge[_-]*" --exclude CVS --exclude ".??*" php-java-bridge-${version}

(phpize && ./configure --with-java=/usr/java/default --with-mono && make) >build.log 2>build.err

# create RPM files if we're root
if test `id -u` = 0 ; then 
    (rpmbuild -tb php-java-bridge_${version}.tar.gz) >build_rpm.log 2>build_rpm.err
else 
    echo "Must be root to re-build the RPM files" >&2 
fi

cp server/JavaBridge.war server/src.zip .
cp -r php_java_lib tests.php5 server
list="FAQ.html INSTALL.J2EE INSTALL.J2SE INSTALL.LINUX INSTALL.STANDALONE documentation server/documentation server/php_java_lib server/test server/tests.php5 server/javabridge.policy src.zip test.bat test.sh FAQ.html INSTALL.J2EE INSTALL.J2SE INSTALL.LINUX INSTALL.STANDALONE documentation server/documentation server/php_java_lib server/test server/tests.php5 server/javabridge.policy src.zip test.bat test.sh README JavaBridge.war"
find $list -type d -name "CVS" -print | xargs rm -rf

chmod +x JavaBridge.war test.sh
# create j2ee download
zip -q -r php-java-bridge_${version}_j2ee.zip $list
