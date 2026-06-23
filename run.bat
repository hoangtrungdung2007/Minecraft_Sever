@echo off
title Minecraft Server 1.21.4 - Paper
color 0A

echo ============================================
echo  Minecraft Server 1.21.4 (Paper)
echo  RAM: 512MB min - 6GB max
echo ============================================
echo.

:: Dùng Java 21 trên ổ D
set JAVA_PATH="D:\tools\jdk21\jdk-21.0.7+6\bin\java.exe"

if not exist %JAVA_PATH% (
    echo [LOI] Khong tim thay Java 21 tai thu muc D:\tools\jdk21!
    echo Vui long cho script tai xong hoac bao AI kiem tra lai.
    pause
    exit /b 1
)

echo [OK] Tim thay Java 21
echo [..] Khoi dong server...
echo.

:: Start server with optimized JVM flags on P-Cores only (Affinity 0FFF) and High Priority
start "MinecraftServer" /B /wait /HIGH /affinity 0FFF %JAVA_PATH% ^
  -Xms512M ^
  -Xmx6G ^
  -XX:+UseG1GC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+DisableExplicitGC ^
  -XX:+AlwaysPreTouch ^
  -XX:G1NewSizePercent=30 ^
  -XX:G1MaxNewSizePercent=40 ^
  -XX:G1HeapRegionSize=8M ^
  -XX:G1ReservePercent=20 ^
  -XX:G1HeapWastePercent=5 ^
  -XX:G1MixedGCCountTarget=4 ^
  -XX:InitiatingHeapOccupancyPercent=15 ^
  -XX:G1MixedGCLiveThresholdPercent=90 ^
  -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:SurvivorRatio=32 ^
  -XX:+PerfDisableSharedMem ^
  -XX:MaxTenuringThreshold=1 ^
  -Dusing.aikars.flags=https://mcflags.emc.gs ^
  -Daikars.new.flags=true ^
  -jar paper-1.21.11-69.jar ^
  --nogui

echo.
echo [SERVER] Server da dung. Nhan phim bat ky de thoat...
pause >nul
