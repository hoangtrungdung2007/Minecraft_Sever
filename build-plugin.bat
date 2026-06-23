@echo off
title Build UtilityTools Plugin
color 0B

echo ============================================
echo  Build UtilityTools Plugin
echo  Yeu cau: Maven 3.6+ va Java 21+
echo ============================================
echo.

:: Dung Maven va Java 21 tren o D
set MAVEN_HOME=D:\tools\maven\apache-maven-3.9.9
set JAVA_HOME=D:\tools\jdk21\jdk-21.0.7+6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

mvn -version >nul 2>&1
if errorlevel 1 (
    echo [LOI] Khong tim thay Maven hoac Java tren o D:\tools!
    pause
    exit /b 1
)

echo [OK] Tim thay Maven va Java 21 tren o D
echo [..] Dang build plugin...
echo.

cd plugins\UtilityTools-src
call mvn clean package -q

if errorlevel 1 (
    echo.
    echo [LOI] Build that bai! Xem log o tren.
    pause
    exit /b 1
)

echo.
echo [OK] Build thanh cong!
echo [..] Sao chep file JAR vao thu muc plugins...

copy "target\UtilityTools-1.0.0.jar" "..\UtilityTools-1.0.0.jar" >nul
echo [OK] Da sao chep: plugins\UtilityTools-1.0.0.jar

echo.
echo ============================================
echo  Hoan tat! Khoi dong lai server de ap dung.
echo ============================================
pause
