#!/bin/sh

echo "package php.java.bridge;" >php/java/bridge/JavaInc.java
echo 'public class JavaInc {' >>php/java/bridge/JavaInc.java
echo 'private static final String data = ' >>php/java/bridge/JavaInc.java
cat $* | sed '/^\/\//d;s/  / /g;s/		/	/g'| sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/"&\\n"+/'  >>php/java/bridge/JavaInc.java
echo '"";' >>php/java/bridge/JavaInc.java
echo 'public static final byte[] bytes = data.getBytes(); }' >>php/java/bridge/JavaInc.java
