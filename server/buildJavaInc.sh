#!/bin/sh

cat $* |
sed '/JAVA_DEBUG/d' | 
sed -f extract.sed | 
sed '/^\/\*/,/\*\/$/d' | 
sed '/^[	 ]*$/d' | 
sed "s|//[^'\"]*\$||;s|\\(^[^'\"]*\\)//.*\$|\\1|;s|[	 ][	 ]*| |g;s|^[	 ]*||;/^\$/d" | sed 's/[ ]*=[ ]*/=/g' | 
sed '/define ("JAVA_PEAR_VERSION"/s|, ".*")|, "'"`cat ../VERSION`"'")|;s/, /,/g' |
sed '/do not delete this line/d' | 
sed ':repeat $!N; s/\n}/}/; t repeat; P; D;' | 
sed ':repeat $!N; s/{[	 ]*\n/{/; t repeat; P; D;' | 
awk -f merge.awk | 
sed -f header.sed >META-INF/java/Java.inc
