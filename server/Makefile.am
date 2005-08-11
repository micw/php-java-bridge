# -*- mode: Makefile; -*-

lib_LTLIBRARIES = libnatcJavaBridge.la
libnatcJavaBridge_la_SOURCES = natcJavaBridge.c
libnatcJavaBridge_la_LDFLAGS = -shared -avoid-version -prefer-pic
libnatcJavaBridge_la_LIBADD =

if COND_GCJ
# create java executable with GNU GCC when --with-java was given
bin_PROGRAMS=$(VM_BINARIES)
EXTRA_PROGRAMS=java RunJavaBridge RunMonoBridge

dist_data_DATA=$(MONO_BRIDGE_EXE)
EXTRA_DATA=MonoBridge.exe

RunJavaBridge_SOURCES= RunJavaBridge.c
java_SOURCES= java.c php/java/bridge/GlobalRef.java php/java/bridge/IDocHandler.java php/java/bridge/ISocketFactory.java php/java/bridge/JavaBridgeClassLoader.java php/java/bridge/JavaBridge.java php/java/bridge/LocalServerSocket.java php/java/bridge/LocalSocketInputStream.java php/java/bridge/LocalSocket.java php/java/bridge/LocalSocketOutputStream.java php/java/bridge/NotImplementedException.java php/java/bridge/Parser.java php/java/bridge/ParserString.java php/java/bridge/ParserTag.java php/java/bridge/PhpMap.java php/java/bridge/Request.java php/java/bridge/Response.java php/java/bridge/Session.java php/java/bridge/TCPServerSocket.java php/java/bridge/Util.java php/java/bridge/global.properties php/java/bridge/DynamicJavaBridgeClassLoader.java php/java/bridge/DynamicClassLoader.java php/java/bridge/ThreadPool.java php/java/bridge/MethodCache.java php/java/bridge/PhpProcedureProxy.java php/java/bridge/PhpProcedure.java php/java/bridge/ISession.java

MonoBridge.exe: $(PHP_MONO)
	$(GCJ) -C php/java/bridge/*.java
	fastjar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/*.properties || jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/*.properties
	mono $(PHP_MONO) JavaBridge.jar
	mv JavaBridge.exe MonoBridge.exe
	rm -f JavaBridge.jar

java_LDADD=libnatcJavaBridge.la
java_LDFLAGS=-rpath $(EXTENSION_DIR)
AM_GCJFLAGS=-fjni

else 
# use a real VM when --with-java=$JAVA_HOME was given

bin_PROGRAMS=RunJavaBridge
RunJavaBridge_SOURCES=RunJavaBridge.c

dist_data_DATA=JavaBridge.jar $(JAVA_BRIDGE_WAR)
EXTRA_DATA=JavaBridge.war

JavaBridge.jar:
	$(PHP_JAVA)/bin/javac php/java/bridge/*.java
	$(PHP_JAVA)/bin/jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class  php/java/bridge/*.properties

JavaBridge.war: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA)/bin/javac -classpath $(SERVLET) php/java/bridge/*.java php/java/servlet/*.java
	-mkdir WEB-INF/lib WEB-INF/classes WEB-INF/cgi
	cp JavaBridge.jar WEB-INF/lib
	echo "Copy your PHP binary into this directory and give it execute permission." >WEB-INF/cgi/README
	echo "For example:" >>WEB-INF/cgi/README
	echo "" >>WEB-INF/cgi/README
	echo "  cp /usr/bin/php ." >>WEB-INF/cgi/README
	echo "  chmod +x php" >>WEB-INF/cgi/README
	echo "" >>WEB-INF/cgi/README
	echo "If this directory does not contain a php executable, the system default" >>WEB-INF/cgi/README
	echo "PHP binary will be called." >>WEB-INF/cgi/README
	cp ../examples/php+jsp/*.jar WEB-INF/lib
	cp ../examples/php+jsp/*.php ../examples/php+jsp/*.jsp  .
	cp -r php WEB-INF/classes
	cp ../test.php .
	$(PHP_JAVA)/bin/jar cMf JavaBridge.war *.php *.jsp META-INF/MANIFEST.MF WEB-INF/web.xml WEB-INF/cgi WEB-INF/lib/*.jar WEB-INF/classes/php/java/servlet/*.class

endif
