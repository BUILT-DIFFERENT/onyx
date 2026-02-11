@rem Gradle startup script for Windows
@if "%DEBUG%" == "" @echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set GRADLE_USER_HOME=%USERPROFILE%\.gradle

if defined JAVA_HOME (
  set "JAVA_HOME=%JAVA_HOME:"=%"
  for /f "tokens=* delims= " %%A in ("%JAVA_HOME%") do set "JAVA_HOME=%%A"
  if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE if exist "C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"

if not defined JAVA_EXE (
  echo ERROR: Java not found. Set JAVA_HOME to a valid JDK directory.
  exit /b 1
)

if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME if exist "%USERPROFILE%\AppData\Local\Android\Sdk" set "ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk"
if defined ANDROID_HOME if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

for %%I in ("%JAVA_EXE%") do set "JAVA_BIN=%%~dpI"
for %%I in ("%JAVA_BIN%..") do set "JAVA_HOME=%%~fI"
set "PATH=%JAVA_BIN%;%PATH%"

"%JAVA_EXE%" -jar "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" %*
set EXIT_CODE=%ERRORLEVEL%
endlocal & exit /b %EXIT_CODE%
