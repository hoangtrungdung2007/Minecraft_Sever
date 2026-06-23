@echo off
set JAVA_HOME=D:\tools\jdk21\jdk-21.0.7+6
set PATH=%JAVA_HOME%\bin;D:\tools\maven\apache-maven-3.9.9\bin;%PATH%
cd /d d:\Minecraft\plugins\UtilityTools-src
call mvn clean package > build_output.txt 2>&1
cd /d d:\Minecraft\plugins
if exist UtilityTools-1.0.0.jar.old del /F /Q UtilityTools-1.0.0.jar.old
ren UtilityTools-1.0.0.jar UtilityTools-1.0.0.jar.old
copy /Y UtilityTools-src\target\UtilityTools-1.0.0.jar UtilityTools-1.0.0.jar > copy_output.txt 2>&1
echo Done > done.txt
