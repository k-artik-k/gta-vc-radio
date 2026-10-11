@rem Minimal Gradle wrapper launcher for Vice City Radio.
@echo off
setlocal
set APP_HOME=%~dp0
if defined JAVA_HOME goto javaHome
set JAVA_EXE=java.exe
goto run
:javaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
:run
"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
