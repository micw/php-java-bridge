#!/bin/sh

cat META-INF/java/JavaBridge.inc META-INF/java/Options.inc META-INF/java/Client.inc META-INF/java/GlobalRef.inc META-INF/java/NativeParser.inc META-INF/java/Parser.inc META-INF/java/Protocol.inc META-INF/java/SimpleParser.inc META-INF/java/JavaProxy.inc |
sed '/JAVA_DEBUG/d' | 
sed -f extract.sed | 
sed '/^\/\*/,/\*\/$/d' | 
sed '/^[	 ]*$/d' | 
sed "s|//[^'\"]*\$||;s|\\(^[^'\"]*\\)//.*\$|\\1|;s|[	 ][	 ]*| |g;s|^[	 ]*||;/^\$/d" | sed 's/[ ]*=[ ]*/=/g' | 
sed '/define ("JAVA_PEAR_VERSION"/s|, ".*")|, "'"`cat ../VERSION`"'")|;s/, /,/g' |
sed '/do not delete this line/d' | 
sed ':repeat $!N; s/\n}/}/; t repeat; P; D;' | 
sed ':repeat $!N; s/{[	 ]*\n/{/; t repeat; P; D;' | 
sed -f header.sed >META-INF/java/Java.inc
