@echo off
echo ==============================================
echo  DANG TAI PHIEN BAN MOI (PAPER 1.21.11)
echo ==============================================
powershell -Command "Invoke-WebRequest -Uri 'https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/69/downloads/paper-1.21.11-69.jar' -OutFile 'paper-1.21.11-69.jar'"

echo.
echo ==============================================
echo  DANG SAO LUU PLAYERDATA VA ADVANCEMENTS...
echo ==============================================
if not exist "backup_playerdata" mkdir "backup_playerdata"
if exist "world\playerdata" xcopy /E /I /Y "world\playerdata" "backup_playerdata\playerdata"
if exist "world\advancements" xcopy /E /I /Y "world\advancements" "backup_playerdata\advancements"

echo.
echo ==============================================
echo  DANG XOA MAP CU DE RESET...
echo ==============================================
if exist "world" rmdir /S /Q "world"
if exist "world_nether" rmdir /S /Q "world_nether"
if exist "world_the_end" rmdir /S /Q "world_the_end"

echo.
echo ==============================================
echo  DANG CAP NHAT FILE RUN.BAT...
echo ==============================================
powershell -Command "(Get-Content run.bat) -replace 'paper-1.21.4-232.jar', 'paper-1.21.11-69.jar' | Set-Content run.bat"

echo.
echo ==============================================
echo  HOAN TAT! XIN MOI CHAY SERVER DE TAO MAP MOI.
echo ==============================================
pause
