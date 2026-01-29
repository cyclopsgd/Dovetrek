@echo off
set JAVA_HOME=C:\Users\cyclo\.jdks\jdk-17.0.17+10
set ANDROID_HOME=C:\Users\cyclo\AppData\Local\Android\Sdk
cd /d C:\Projects\scouterouteplanner
call gradlew.bat assembleDebug
