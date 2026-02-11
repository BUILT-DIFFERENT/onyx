@echo off
set JAVA_HOME=C:\Progra~1\Microsoft\jdk-17.0.16.8-hotspot
echo JAVA_HOME=%C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\%
if exist %C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\%\bin\java.exe echo JAVA_EXISTS
%C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\%\bin\java.exe -version
cd /d C:\onyx\apps\android
.\gradlew.bat :app:assembleDebug
