
@echo off

set "CATALINA_HOME=C:\GjergjKadriu\Documents\GitHub\apache-tomcat-10.1.61"
set "CATALINA_BASE=%CATALINA_HOME%"

cd /d "%CATALINA_HOME%\bin"

call catalina.bat run
