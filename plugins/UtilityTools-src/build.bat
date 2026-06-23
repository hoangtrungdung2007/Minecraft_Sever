@echo off
echo Dang build plugin, vui long doi...
call mvn clean package
echo.
echo Copy file jar sang thu muc plugins...
copy /Y target\UtilityTools-1.0.0.jar ..\UtilityTools-1.0.0.jar
echo.
echo Hoan thanh! Hay vao game va go lenh /reload confirm
pause
