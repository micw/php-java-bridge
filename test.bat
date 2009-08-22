@echo off
rem Test script for Windows, type test.bat or double-click on test.bat
rem and click "run".
rem Then check RESULT.html and JavaBridge.log.
@echo on
java -classpath JavaBridge.war TestInstallation

pause
