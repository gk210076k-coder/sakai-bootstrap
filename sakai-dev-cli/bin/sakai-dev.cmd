@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%..\target\sakai-dev-cli.jar"
if not exist "%JAR%" (
  echo Sakai CLI JAR was not built. Run: mvn -f "%SCRIPT_DIR%..\pom.xml" package 1>&2
  exit /b 1
)
java -jar "%JAR%" %*
exit /b %ERRORLEVEL%
