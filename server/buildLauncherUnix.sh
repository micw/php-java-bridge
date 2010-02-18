#!/bin/sh

echo "package php.java.bridge;" >php/java/bridge/LauncherUnix.java
echo 'public class LauncherUnix {' >>php/java/bridge/LauncherUnix.java
echo 'private static final String data = ' >>php/java/bridge/LauncherUnix.java
cat $* | sed '/^\/\//d;s/  / /g;s/		/	/g'| sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/"&\\n"+/'  >>php/java/bridge/LauncherUnix.java
echo '"";' >>php/java/bridge/LauncherUnix.java
echo 'public static final byte[] bytes = data.getBytes(); }' >>php/java/bridge/LauncherUnix.java
