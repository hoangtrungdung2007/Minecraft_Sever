@echo off
cd /d d:\Minecraft\plugins
if exist UtilityTools-1.0.0.jar.old del /F /Q UtilityTools-1.0.0.jar.old
ren UtilityTools-1.0.0.jar UtilityTools-1.0.0.jar.old
copy /Y UtilityTools-src\target\UtilityTools-1.0.0.jar UtilityTools-1.0.0.jar
echo Copy done!
