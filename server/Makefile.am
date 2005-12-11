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
java_SOURCES= java.c php/java/bridge/ConstructorCache.java php/java/bridge/DynamicClassLoader.java php/java/bridge/DynamicJavaBridgeClassLoader.java php/java/bridge/GlobalRef.java php/java/bridge/IDocHandler.java php/java/bridge/Invocable.java php/java/bridge/ISession.java php/java/bridge/ISocketFactory.java php/java/bridge/JavaBridgeClassLoader.java php/java/bridge/JavaBridge.java php/java/bridge/JavaBridgeRunner.java php/java/bridge/LocalServerSocket.java php/java/bridge/LocalSocketInputStream.java php/java/bridge/LocalSocket.java php/java/bridge/LocalSocketOutputStream.java php/java/bridge/MethodCache.java php/java/bridge/NotImplementedException.java php/java/bridge/Options.java php/java/bridge/Parser.java php/java/bridge/ParserString.java php/java/bridge/ParserTag.java php/java/bridge/PhpMap.java php/java/bridge/PhpProcedure.java php/java/bridge/PhpProcedureProxy.java php/java/bridge/Request.java php/java/bridge/Response.java php/java/bridge/SessionFactory.java php/java/bridge/Session.java php/java/bridge/TCPServerSocket.java php/java/bridge/ThreadPool.java php/java/bridge/Util.java php/java/bridge/http/ContextFactory.java php/java/bridge/http/Context.java php/java/bridge/http/ContextRunner.java php/java/bridge/http/ContextServer.java php/java/bridge/http/HttpRequest.java php/java/bridge/http/HttpResponse.java php/java/bridge/http/HttpServer.java





MonoBridge.exe: $(PHP_MONO)
	$(GCJ) -C php/java/bridge/*.java php/java/bridge/http/*.java
	fastjar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties || jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/*.class php/java/bridge/http/*.class php/java/bridge/*.properties
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

dist_data_DATA=JavaBridge.jar $(JAVA_BRIDGE_WAR) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR)
EXTRA_DATA=JavaBridge.war

JavaBridge.jar:
	$(PHP_JAVA)/bin/javac php/java/bridge/http/*.java php/java/bridge/*.java
	$(PHP_JAVA)/bin/jar cMf JavaBridge.jar META-INF/MANIFEST.MF php/java/bridge/http/*.class php/java/bridge/*.class  php/java/bridge/*.properties

php-servlet.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA)/bin/javac -classpath .:$(SERVLET) php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java
	$(PHP_JAVA)/bin/jar cf php-servlet.jar php/java/servlet/*.class

script-api.jar: JavaBridge.jar $(SERVLET)
	$(PHP_JAVA)/bin/javac -classpath .:$(SERVLET) javax/script/*.java
	$(PHP_JAVA)/bin/jar cf script-api.jar javax/script/*.class

php-script.jar: JavaBridge.jar $(SCRIPT) $(SERVLET)
	$(PHP_JAVA)/bin/javac -classpath .:$(SERVLET):$(SCRIPT) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java
	$(PHP_JAVA)/bin/jar cf php-script.jar php/java/script/*.class

php-faces.jar: JavaBridge.jar $(FACES) $(SERVLET)
	$(PHP_JAVA)/bin/javac -classpath .:$(SERVLET):$(SCRIPT):$(FACES) php/java/bridge/http/*.java php/java/bridge/*.java php/java/script/*.java php/java/faces/*.java
	$(PHP_JAVA)/bin/jar cf php-faces.jar php/java/faces/*.class

JavaBridge.war: JavaBridge.jar $(SERVLET) $(JAVA_BRIDGE_SERVLET_JAR) $(SCRIPT) $(JAVA_BRIDGE_SCRIPT_JAR) $(FACES) $(JAVA_BRIDGE_FACES_JAR)
	$(PHP_JAVA)/bin/javac -classpath $(SERVLET):$(SCRIPT) php/java/bridge/http/*.java php/java/bridge/*.java php/java/servlet/*.java

	-mkdir WEB-INF/lib WEB-INF/classes WEB-INF/cgi
	cp JavaBridge.jar $(SCRIPT) $(JAVA_BRIDGE_SERVLET_JAR) $(JAVA_BRIDGE_SCRIPT_JAR) $(JAVA_BRIDGE_FACES_JAR) ../unsupported/*.jar WEB-INF/lib
	rm WEB-INF/lib/servlet-api.jar
	cp ../examples/php+jsp/*.jar WEB-INF/lib
	cp -r ../examples/java-server-faces .
	cp ../examples/php+jsp/*.php ../examples/php+jsp/*.jsp  .
	cp ../test.php .
	$(PHP_JAVA)/bin/jar cMf JavaBridge.war *.php java-server-faces *.jsp META-INF/MANIFEST.MF WEB-INF/web.xml WEB-INF/faces-config.xml WEB-INF/cgi WEB-INF/lib/*.jar

endif
