@echo off
cd /d "%~dp0"
java -version
if errorlevel 1 exit /b 2
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 9.8.2 not found. Follow README GitHub Actions workflow instructions.
  exit /b 3
)
gradle --no-daemon clean build
exit /b %errorlevel%
