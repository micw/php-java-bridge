#!/bin/sh
echo "package php.java.bridge;" >php/java/bridge/LauncherWindows.java
echo 'public class LauncherWindows {' >>php/java/bridge/LauncherWindows.java
echo 'public static final byte[] bytes = new byte[]{' >>php/java/bridge/LauncherWindows.java
cat  $* | od -vb | sed 's/^[0-7]*//;s/^[^ ]* //;s/[0-7][0-7]*/(byte)0&,/g' | split -l600
cat xaa >>php/java/bridge/LauncherWindows.java
rm xaa
echo '};}' >>php/java/bridge/LauncherWindows.java

echo "package php.java.bridge;" >php/java/bridge/LauncherWindows2.java
echo 'public class LauncherWindows2 {' >>php/java/bridge/LauncherWindows2.java
echo 'public static final byte[] bytes = new byte[]{' >>php/java/bridge/LauncherWindows2.java
cat xab >>php/java/bridge/LauncherWindows2.java
rm xab
echo '};}' >>php/java/bridge/LauncherWindows2.java

echo "package php.java.bridge;" >php/java/bridge/LauncherWindows3.java
echo 'public class LauncherWindows3 {' >>php/java/bridge/LauncherWindows3.java
echo 'public static final byte[] bytes = new byte[]{' >>php/java/bridge/LauncherWindows3.java
cat xac >>php/java/bridge/LauncherWindows3.java
rm xac
echo '};}' >>php/java/bridge/LauncherWindows3.java

echo "package php.java.bridge;" >php/java/bridge/LauncherWindows4.java
echo 'public class LauncherWindows4 {' >>php/java/bridge/LauncherWindows4.java
echo 'public static final byte[] bytes = new byte[]{' >>php/java/bridge/LauncherWindows4.java
cat xad >>php/java/bridge/LauncherWindows4.java
rm xad
echo '};}' >>php/java/bridge/LauncherWindows4.java
