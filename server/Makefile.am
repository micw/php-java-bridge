# -*- mode: Makefile; -*-

lib_LTLIBRARIES = $(JNI_LIBS)
EXTRA_LTLIBRARIES = libnatcJavaBridge.la
libnatcJavaBridge_la_SOURCES = natcJavaBridge.c
libnatcJavaBridge_la_LDFLAGS = -rpath $(EXTENSION_DIR) -shared -avoid-version -prefer-pic
libnatcJavaBridge_la_LIBADD =
java_SOURCES=php/java/bridge/JavaBridgeIllegalArgumentException.java php/java/bridge/Base64EncodingOutputBuffer.java php/java/bridge/BaseThreadPool.java php/java/bridge/ChainsawLogger.java php/java/bridge/ClassicParserString.java php/java/bridge/ClassicResponse.java php/java/bridge/ConstructorCache.java php/java/bridge/DefaultOptions.java php/java/bridge/DynamicClassLoader.java php/java/bridge/DynamicJavaBridgeClassLoader.java php/java/bridge/FileLogger.java php/java/bridge/GlobalRef.java php/java/bridge/HexOutputBuffer.java php/java/bridge/IDocHandler.java php/java/bridge/ILogger.java php/java/bridge/IntegerComparator.java php/java/bridge/Invocable.java php/java/bridge/ISession.java php/java/bridge/ISocketFactory.java php/java/bridge/JarLibraryPath.java php/java/bridge/JavaBridgeClassLoader.java php/java/bridge/JavaBridge.java php/java/bridge/JavaBridgeRunner.java php/java/bridge/JavaBridgeSecurityManager.java php/java/bridge/LocalServerSocket.java php/java/bridge/LocalSocketInputStream.java php/java/bridge/LocalSocket.java php/java/bridge/LocalSocketOutputStream.java php/java/bridge/Log4jLogger.java php/java/bridge/MethodCache.java php/java/bridge/NotImplementedException.java php/java/bridge/Options.java php/java/bridge/Parser.java php/java/bridge/ParserString.java php/java/bridge/ParserTag.java php/java/bridge/PhpArray.java php/java/bridge/PhpExactNumber.java php/java/bridge/PhpMap.java php/java/bridge/PhpParserString.java php/java/bridge/PhpProcedure.java php/java/bridge/PhpProcedureProxy.java php/java/bridge/PhpString.java php/java/bridge/Request.java php/java/bridge/Response.java php/java/bridge/SessionFactory.java php/java/bridge/Session.java php/java/bridge/SimpleJavaBridgeClassLoader.java php/java/bridge/SimplePhpString.java php/java/bridge/Snarf.java php/java/bridge/StandaloneGCC.java php/java/bridge/Standalone.java php/java/bridge/StandardOptions.java php/java/bridge/StringCache.java php/java/bridge/TCPServerSocket.java php/java/bridge/ThreadPool.java php/java/bridge/Util.java php/java/bridge/http/AbstractChannel.java php/java/bridge/http/AbstractChannelName.java php/java/bridge/http/ContextFactory.java php/java/bridge/http/Context.java php/java/bridge/http/ContextRunner.java php/java/bridge/http/ContextServer.java php/java/bridge/http/HttpRequest.java php/java/bridge/http/HttpResponse.java php/java/bridge/http/HttpServer.java php/java/bridge/http/IContextFactory.java php/java/bridge/http/IContextFactoryVisitor.java php/java/bridge/http/IContextServer.java php/java/bridge/http/PipeContextServer.java php/java/bridge/http/SimpleContextFactory.java php/java/bridge/http/SocketContextServer.java


java_LDADD=libnatcJavaBridge.la
java_LDFLAGS=--main=php.java.bridge.StandaloneGCC -rpath $(EXTENSION_DIR)
AM_GCJFLAGS=-fjni
bin_PROGRAMS=$(VM_BINARIES)

if COND_GCJ
# create java executable with GNU GCC when --with-java was given
EXTRA_PROGRAMS=java RunJavaBridge RunMonoBridge

dist_data_DATA=$(MONO_BRIDGE_EXE) stamp
# see MONO_BRIDGE_EXE in configure.in
mono_dlls=ICSharpCode.SharpZipLib.dll IKVM.AWT.WinForms.dll IKVM.GNU.Classpath.dll IKVM.Runtime.dll
EXTRA_DATA=MonoBridge.exe $(mono_dlls)

RunJavaBridge_SOURCES= RunJavaBridge.c

stamp:
	date >stamp

MonoBridge.exe: $(PHP_MONO)
	$(GCJ) -classpath .:$(SCRIPT) -C php/java/bridge/*.java php/java/bridge/http/*.java
	fastjar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties || jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties
	$(PHP_JAVA) $(PHP_MONO)/ikvmc.exe JavaBridge.jar
	for i in $(mono_dlls); do cp $(PHP_MONO)/$$i .; done
	mv JavaBridge.exe MonoBridge.exe
	rm -f JavaBridge.jar

else 
# use a real VM when --with-java=$JAVA_HOME was given

EXTRA_PROGRAMS=java RunJavaBridge
RunJavaBridge_SOURCES=RunJavaBridge.c

dist_data_DATA=javabridge.policy JavaBridge.jar $(JAVA_BRIDGE_WAR) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR) stamp
EXTRA_DATA=JavaBridge.war

stamp:
	date >stamp

JavaBridge.jar:
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath . php/java/bridge/http/*.java php/java/bridge/*.java
	$(PHP_JAVA)/bin/jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/http/*.class php/java/bridge/*.class  php/java/bridge/*.properties
	chmod +x JavaBridge.jar

php-servlet.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET) php/java/script/*.java php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java
	$(PHP_JAVA)/bin/jar cf php-servlet.jar php/java/servlet/*.class

script-api.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET) javax/script/*.java
	$(PHP_JAVA)/bin/jar cf script-api.jar javax/script/*.class

php-script.jar: JavaBridge.jar $(SCRIPT) $(SERVLET)
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET):$(SCRIPT) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java
	$(PHP_JAVA)/bin/jar cf php-script.jar META-INF/services/javax.script.ScriptEngineFactory php/java/script/*.class

php-faces.jar: JavaBridge.jar $(FACES) $(SERVLET)
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath .:$(SERVLET):$(SCRIPT):$(FACES) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java php/java/faces/*.java
	$(PHP_JAVA)/bin/jar cf php-faces.jar php/java/faces/*.class

JavaBridge.war: JavaBridge.jar $(SERVLET) $(JAVA_BRIDGE_SERVLET_JAR) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR) $(FACES) $(JAVA_BRIDGE_FACES_JAR)
	$(PHP_JAVA)/bin/javac $(SOURCE_VERSION) -classpath $(SERVLET):$(SCRIPT) php/java/script/*.java php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java
	-mkdir WEB-INF/lib WEB-INF/classes
	cp JavaBridge.jar $(SCRIPT) $(JAVA_BRIDGE_SERVLET_JAR) $(JAVA_BRIDGE_SCRIPT_JAR) $(JAVA_BRIDGE_FACES_JAR) ../unsupported/*.jar WEB-INF/lib
	rm WEB-INF/lib/servlet-api.jar WEB-INF/lib/log4j.jar
	cp ../examples/php+jsp/*.jar WEB-INF/lib
	cp -r ../examples/java-server-faces .
	cp ../examples/php+jsp/*.php ../examples/php+jsp/*.jsp  .
	cp ../test.php .
	rm -f META-INF/MANIFEST.MF.standalone
	mv META-INF/MANIFEST.MF META-INF/MANIFEST.MF.standalone
	sed '/Main-Class/s/php.java.bridge.Standalone/TestInstallation/' <META-INF/MANIFEST.MF.standalone >META-INF/MANIFEST.MF
	$(PHP_JAVA)/bin/jar cMf JavaBridge.war *.php java-server-faces *.jsp META-INF/MANIFEST.MF WEB-INF/web.xml WEB-INF/faces-config.xml WEB-INF/cgi/*.sh WEB-INF/cgi/*.ini WEB-INF/cgi/README WEB-INF/lib/*.jar
	rm -f META-INF/MANIFEST.MF
	mv META-INF/MANIFEST.MF.standalone META-INF/MANIFEST.MF
endif
