#!/bin/sh
set -x
rm -rf [^C][^V][^S]* .??* *~
cvs -Q update -APd 
find . -print | xargs touch
dirs=`ls -l | grep '^d' | fgrep -v CVS | awk '{print $8}'`
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
cp -r php_java_lib tests.php5 server

mkdir MONO.STANDALONE
for i in ICSharpCode.SharpZipLib.dll IKVM.GNU.Classpath.dll IKVM.Runtime.dll MonoBridge.exe; do
 cp modules/$i MONO.STANDALONE
done
cp tests.mono+net/test.php tests.mono+net/sample_lib.cs tests.mono+net/sample_lib.dll tests.mono+net/load_assembly.php MONO.STANDALONE
cp server/META-INF/java/Mono.inc MONO.STANDALONE
cp README.MONO+NET MONO.STANDALONE

mkdir JAVA.STANDALONE
for i in JavaBridge.jar javabridge.policy php-script.jar script-api.jar; do
 cp modules/$i JAVA.STANDALONE
done
cp test.php JAVA.STANDALONE
cp examples/bench/exceltest.jar examples/bench/ExcelTest.java examples/bench/excel_antitest.php JAVA.STANDALONE
sed 's|\.\./\.\./unsupported/||' <examples/bench/bench.php >JAVA.STANDALONE/bench.php
cp unsupported/poi.jar JAVA.STANDALONE
cp unsupported/log4j.jar JAVA.STANDALONE
cp server/META-INF/java/Java.inc JAVA.STANDALONE
cp INSTALL.STANDALONE JAVA.STANDALONE

mv server documentation
mv README FAQ.html documentation
list="JAVA.STANDALONE MONO.STANDALONE INSTALL.J2EE INSTALL.J2SE INSTALL.LINUX documentation/API documentation/README documentation/FAQ.html src.zip JavaBridge.war documentation/server/documentation documentation/server/php_java_lib documentation/server/test documentation/server/tests.php5 documentation/server/javabridge.policy"
find $list -type d -name "CVS" -print | xargs rm -rf

chmod +x JavaBridge.war
# create j2ee download
zip -q -r php-java-bridge_${version}_j2ee.zip $list
rm -rf $dirs
cvs -Q update -APd 
