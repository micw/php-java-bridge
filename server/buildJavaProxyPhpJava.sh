#!/bin/sh

echo "package php.java.bridge;" >php/java/bridge/JavaProxy.java
echo 'public class JavaProxy {' >>php/java/bridge/JavaProxy.java
echo 'private static final String data = ' >>php/java/bridge/JavaProxy.java
cat $* | sed '/^\/\//d;s/  / /g;s/		/	/g'| sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/"&\\n"+/'  >>php/java/bridge/JavaProxy.java
echo '"";' >>php/java/bridge/JavaProxy.java
echo 'public static final byte[] bytes = data.getBytes(); }' >>php/java/bridge/JavaProxy.java
