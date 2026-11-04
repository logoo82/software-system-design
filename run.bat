@echo off
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -jar dist\ssd_ip_cp4_2026f_223247_app.jar %*
if errorlevel 1 pause
