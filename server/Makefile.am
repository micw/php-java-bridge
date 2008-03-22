# -*- mode: Makefile; -*-

lib_LTLIBRARIES = $(JNI_LIBS)
EXTRA_LTLIBRARIES = libnatcJavaBridge.la
libnatcJavaBridge_la_SOURCES = natcJavaBridge.c
libnatcJavaBridge_la_LDFLAGS = -rpath $(EXTENSION_DIR) -shared -avoid-version -prefer-pic
libnatcJavaBridge_la_LIBADD = META-INF/java/Java.inc
java_SOURCES=php/java/bridge/JavaBridgeIllegalStateException.java php/java/bridge/AppThreadPool.java php/java/bridge/Base64EncodingOutputBuffer.java php/java/bridge/ChainsawLogger.java php/java/bridge/ConstructorCache.java php/java/bridge/DefaultOptions.java php/java/bridge/DynamicClassLoader.java php/java/bridge/DynamicHttpURLConnectionHandler.java php/java/bridge/DynamicJarURLConnection.java php/java/bridge/DynamicJavaBridgeClassLoader.java php/java/bridge/FileLogger.java php/java/bridge/GlobalRef.java php/java/bridge/HexOutputBuffer.java php/java/bridge/IDocHandler.java php/java/bridge/IJavaBridgeFactory.java php/java/bridge/ILogger.java php/java/bridge/IntegerComparator.java php/java/bridge/Invocable.java php/java/bridge/ISession.java php/java/bridge/ISocketFactory.java php/java/bridge/JarLibraryPath.java php/java/bridge/JavaBridgeClassLoader.java php/java/bridge/JavaBridgeFactory.java php/java/bridge/JavaBridgeIllegalArgumentException.java php/java/bridge/JavaBridge.java php/java/bridge/JavaBridgeRunner.java php/java/bridge/JavaBridgeSecurityManager.java php/java/bridge/LocalServerSocket.java php/java/bridge/LocalSocketInputStream.java php/java/bridge/LocalSocket.java php/java/bridge/LocalSocketOutputStream.java php/java/bridge/Log4jLogger.java php/java/bridge/MethodCache.java php/java/bridge/NotImplementedException.java php/java/bridge/Options.java php/java/bridge/Parser.java php/java/bridge/ParserString.java php/java/bridge/ParserTag.java php/java/bridge/PhpArray.java php/java/bridge/PhpExactNumber.java php/java/bridge/PhpMap.java php/java/bridge/PhpParserString.java php/java/bridge/PhpProcedure.java php/java/bridge/PhpProcedureProxy.java php/java/bridge/PhpString.java php/java/bridge/Request.java php/java/bridge/Response.java php/java/bridge/SessionFactory.java php/java/bridge/Session.java php/java/bridge/SimpleJavaBridgeClassLoader.java php/java/bridge/SimpleLog4jLogger.java php/java/bridge/SimplePhpString.java php/java/bridge/StandaloneGCC.java php/java/bridge/Standalone.java php/java/bridge/StringCache.java php/java/bridge/TCPServerSocket.java php/java/bridge/ThreadPool.java php/java/bridge/Util.java php/java/bridge/http/AbstractChannel.java php/java/bridge/http/AbstractChannelName.java php/java/bridge/http/ContextFactory.java php/java/bridge/http/Context.java php/java/bridge/http/ContextRunner.java php/java/bridge/http/ContextServer.java php/java/bridge/http/HttpRequest.java php/java/bridge/http/HttpResponse.java php/java/bridge/http/HttpServer.java php/java/bridge/http/IContextFactory.java php/java/bridge/http/IContextFactoryVisitor.java php/java/bridge/http/IContext.java php/java/bridge/http/IContextServer.java php/java/bridge/http/PipeContextServer.java php/java/bridge/http/SimpleContextFactory.java php/java/bridge/http/SocketContextServer.java


JavaRaw.inc:
	cat META-INF/java/JavaBridge.inc META-INF/java/Options.inc META-INF/java/Client.inc META-INF/java/GlobalRef.inc META-INF/java/NativeParser.inc META-INF/java/Parser.inc META-INF/java/Protocol.inc META-INF/java/SimpleParser.inc META-INF/java/JavaProxy.inc | sed -f extract.sed | sed '/^\/\*/,/\*\/$$/d' >JavaRaw.inc

META-INF/java/Java.inc: JavaRaw.inc
	cat JavaRaw.inc | sed '/do not delete this line/d' >META-INF/java/Java.inc

META-INF/java/JavaProxy.php: META-INF/java/Java.inc
	cat META-INF/java/Java.inc | sed -f prepend.sed >META-INF/java/JavaProxy.php

META-INF/java/Mono.inc: JavaRaw.inc
	cat JavaRaw.inc | sed -f append.sed | sed 's/JAVA/MONO/g;s/java/mono/g;s/Java/Mono/g;s/updateJarLibraryPath/updateLibraryPath/;s/^.*do not delete this line.*$$/$$name="cli.".$$name;/;' >META-INF/java/Mono.inc


php/java/bridge/JavaInc.java: META-INF/java/Java.inc
	echo "package php.java.bridge;" >php/java/bridge/JavaInc.java
	echo 'public class JavaInc {' >>php/java/bridge/JavaInc.java
	echo 'private static final String data = ' >>php/java/bridge/JavaInc.java
	cat META-INF/java/Java.inc | sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/"&\\n"+/'  >>php/java/bridge/JavaInc.java
	echo '"";' >>php/java/bridge/JavaInc.java
	echo 'public static final byte[] bytes = data.getBytes(); }' >>php/java/bridge/JavaInc.java

java_LDADD=libnatcJavaBridge.la
java_LDFLAGS=--main=php.java.bridge.StandaloneGCC -rpath $(EXTENSION_DIR)
AM_GCJFLAGS=-fjni -w
bin_PROGRAMS=$(VM_BINARIES)



MonoBridge.exe: $(PHP_MONO) META-INF/java/Mono.inc
	$(GCJ) -w -ftarget=1.4 -classpath .:$(SCRIPT) -C php/java/bridge/*.java php/java/bridge/http/*.java
	fastjar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties || jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties
	$(PHP_MONO_BINARY) $(PHP_MONO)/ikvmc.exe JavaBridge.jar
	for i in $(mono_dlls); do cp $(PHP_MONO)/$$i .; done
	mv JavaBridge.exe MonoBridge.exe
	rm -f JavaBridge.jar


EXTRA_PROGRAMS=java RunJavaBridge RunMonoBridge
RunJavaBridge_SOURCES=RunJavaBridge.c
RunMonoBridge_SOURCES=RunMonoBridge.c

dist_data_DATA=$(JAVA_BRIDGE_JAR) $(JAVA_BRIDGE_WAR) $(MONO_BRIDGE_EXE) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR) stamp
mono_dlls=ICSharpCode.SharpZipLib.dll IKVM.AWT.WinForms.dll IKVM.GNU.Classpath.dll IKVM.Runtime.dll
EXTRA_DATA=javabridge.policy JavaBridge.jar JavaBridge.war MonoBridge.exe $(mono_dlls) script-api.jar php-script.jar 
java_inc=-C META-INF java/JavaBridge.inc -C META-INF java/Java.inc -C META-INF java/JavaProxy.inc -C META-INF java/Mono.inc -C META-INF java/NativeParser.inc -C META-INF java/Options.inc -C META-INF java/Parser.inc -C META-INF java/Protocol.inc -C META-INF java/SimpleParser.inc -C META-INF java/JavaProxy.php -C META-INF java/README

stamp:
	date >stamp

JavaBridge.jar: META-INF/java/Java.inc META-INF/java/Mono.inc
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath . php/java/bridge/http/*.java php/java/bridge/*.java
	$(PHP_JAVA_DIR)/bin/jar cMf JavaBridge.jar META-INF/java/*.inc META-INF/java/README META-INF/MANIFEST.MF php/java/bridge/http/*.class php/java/bridge/*.class  php/java/bridge/*.properties
	chmod +x JavaBridge.jar

TestInstallation.class: TestInstallation.java
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath . TestInstallation.java

php-servlet.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET) php/java/script/*.java php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java php/java/servlet/fastcgi/*.java
	$(PHP_JAVA_DIR)/bin/jar cf php-servlet.jar php/java/servlet/*.class php/java/servlet/fastcgi/*.class

script-api.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET) javax/script/*.java
	$(PHP_JAVA_DIR)/bin/jar cf script-api.jar javax/script/*.class

php-script.jar: JavaBridge.jar $(SCRIPT) $(SERVLET)
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET):$(SCRIPT) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java
	$(PHP_JAVA_DIR)/bin/jar cf php-script.jar META-INF/services/javax.script.ScriptEngineFactory php/java/script/*.class

php-faces.jar: JavaBridge.jar $(FACES) $(SERVLET)
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET):$(SCRIPT):$(FACES) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java php/java/faces/*.java
	$(PHP_JAVA_DIR)/bin/jar cf php-faces.jar php/java/faces/*.class

src.zip: META-INF/java/Mono.inc META-INF/java/JavaProxy.php META-INF/java/Mono.inc
	$(PHP_JAVA_DIR)/bin/jar cf src.zip WEB-INF/cgi/launcher.c WEB-INF/cgi/launcher.sh WEB-INF/cgi/README WEB-INF/cgi/rename* javax/script/*.java php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/fastcgi/*.java php/java/servlet/*.java php/java/script/*.java $(java_inc)


JavaBridge.war: src.zip JavaBridge.jar $(SERVLET) $(JAVA_BRIDGE_SERVLET_JAR) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR) $(FACES) $(JAVA_BRIDGE_FACES_JAR) TestInstallation.class META-INF/java/JavaProxy.php
	$(PHP_JAVA_DIR)/bin/javac $(SOURCE_VERSION) -classpath $(SERVLET):$(SCRIPT) php/java/script/*.java php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java php/java/servlet/fastcgi/*.java
	-mkdir WEB-INF/lib WEB-INF/classes
	cp JavaBridge.jar $(SCRIPT) $(JAVA_BRIDGE_SERVLET_JAR) $(JAVA_BRIDGE_SCRIPT_JAR) $(JAVA_BRIDGE_FACES_JAR) ../unsupported/*.jar WEB-INF/lib
	cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux/x86-sunos/;s/^extension=.*$$/;&/' >WEB-INF/cgi/php-cgi-x86-sunos.ini
	cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh | sed 's/i386-linux/x86-sunos/g' >WEB-INF/cgi/php-cgi-x86-sunos.sh

	cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux/i386-freebsd/;s/^extension=.*$$/;&/' >WEB-INF/cgi/php-cgi-i386-freebsd.ini
	cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh | sed 's/i386-linux/i386-freebsd/g' >WEB-INF/cgi/php-cgi-i386-freebsd.sh

	cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/^extension=.*$$/;&/' >WEB-INF/cgi/php-cgi-i386-linux.ini
	cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh  >WEB-INF/cgi/php-cgi-x86-linux.sh

	cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux.so/x86-windows.dll/;s/^extension=.*$$/;&/' >WEB-INF/cgi/php.ini

	rm WEB-INF/lib/servlet-api.jar WEB-INF/lib/log4j.jar
	cp ../examples/php+jsp/*.jar WEB-INF/lib
	cp -r ../examples/php+jsp/locale ../examples/php+jsp/*.php ../examples/php+jsp/*.jsp  .
	cp ../test.php .
	rm -f META-INF/MANIFEST.MF.standalone
	mv META-INF/MANIFEST.MF META-INF/MANIFEST.MF.standalone
	sed '/Main-Class/s/php.java.bridge.Standalone/TestInstallation/' <META-INF/MANIFEST.MF.standalone >META-INF/MANIFEST.MF
	$(PHP_JAVA_DIR)/bin/jar cMf JavaBridge.war TestInstallation*.class *.php locale/* *.jsp META-INF/MANIFEST.MF WEB-INF/web.xml WEB-INF/cgi/* WEB-INF/cgi/README WEB-INF/lib/*.jar $(java_inc)
	rm -f META-INF/MANIFEST.MF
	rm -rf java
	mv META-INF/MANIFEST.MF.standalone META-INF/MANIFEST.MF
