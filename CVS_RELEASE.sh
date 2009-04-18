#!/bin/sh
set -x
LANG=C

rm -rf [^C][^V][^S]* .??* *~
cvs -Q update -APd 
find . -print0 | xargs -0 touch
dirs=`ls -l | grep '^d' | fgrep -v CVS | awk '{print $9}'`
find $dirs -name "CVS" -print | xargs rm -rf

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
cp -r php_java_lib tests.php5 tests.jsr223 server

mkdir MONO+NET.STANDALONE
for i in ICSharpCode.SharpZipLib.dll IKVM.GNU.Classpath.dll IKVM.Runtime.dll MonoBridge.exe; do
 cp modules/$i MONO+NET.STANDALONE
done
cp examples/gui/gtk-button.php examples/gui/gtk-fileselector.php tests.mono+net/test.php tests.mono+net/sample_lib.cs tests.mono+net/sample_lib.dll tests.mono+net/load_assembly.php MONO+NET.STANDALONE
mkdir MONO+NET.STANDALONE/mono
cp server/META-INF/java/Mono.inc MONO+NET.STANDALONE/mono
cp README.MONO+NET MONO+NET.STANDALONE

cp JavaBridge.war JavaBridgeTemplate.war
for i in 'META-INF/*' 'WEB-INF/lib/[^pJ]*.jar' 'WEB-INF/lib/poi.jar' 'WEB-INF/cgi/*' 'WEB-INF/web.xml' 'WEB-INF/platform/*' 'locale/*' 'java/*' '*.class' '*.jsp' '*.rpt*' '*.php'; do
  zip -d JavaBridgeTemplate.war "$i"; 
done
cat examples/php+jsp/settings.php | sed 3d >./index.php
echo '<?php phpinfo();echo "<br><hr><br>"; echo java("java.lang.System")->getProperties(); ?>' >test.php
rm -rf WEB-INF; mkdir WEB-INF
cp server/example-web.xml WEB-INF/web.xml
zip JavaBridgeTemplate.war index.php test.php
zip JavaBridgeTemplate.war WEB-INF/web.xml

cp  src.zip README FAQ.html PROTOCOL.TXT INSTALL.STANDALONE INSTALL.J2EE INSTALL.J2SE NEWS documentation
mv examples documentation
mv server documentation
list="MONO+NET.STANDALONE  documentation/API documentation/examples documentation/README documentation/FAQ.html documentation/PROTOCOL.TXT documentation/INSTALL.J2EE documentation/INSTALL.J2SE documentation/INSTALL.STANDALONE documentation/src.zip documentation/NEWS JavaBridge.war documentation/server/documentation documentation/server/php_java_lib documentation/server/tests.jsr223 documentation/server/tests.php5"
find $list -type d -name "CVS" -print | xargs rm -rf


chmod +x JavaBridge.war

# create j2ee download
zip -q -r php-java-bridge_${version}_documentation.zip $list
mv JavaBridgeTemplate.war "JavaBridgeTemplate`echo ${version}|sed 's/\.//g'`.war"
rm -rf $dirs
cvs -Q update -APd 
