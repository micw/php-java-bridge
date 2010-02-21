#!/bin/sh

echo "package php.java.bridge;" >php/java/bridge/PhpDebuggerPHP.java
echo 'public class PhpDebuggerPHP {' >>php/java/bridge/PhpDebuggerPHP.java
echo 'private static final String data = ' >>php/java/bridge/PhpDebuggerPHP.java
cat $* | sed '/^\/\//d;s/  / /g;s/		/	/g'| sed 's/\\/\\\\/g;s/"/\\"/g;s/.*/"&\\n"+/'  >>php/java/bridge/PhpDebuggerPHP.java
echo '"";' >>php/java/bridge/PhpDebuggerPHP.java
echo 'public static final byte[] bytes = data.getBytes(); }' >>php/java/bridge/PhpDebuggerPHP.java
