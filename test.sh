#!/bin/sh
# Test script for Linux/Unix, type sh test.sh or double-click on test.sh
# and click "run".
# Then check RESULT.html, ext/script-error.log and ext/JavaBridge.log.

java -classpath JavaBridge.war TestInstallation

echo 'Press any key.'
read key
