@echo off
chcp 65001 > nul
echo ========================================================
echo   [SERVER] KHOI DONG FTP SERVER DASHBOARD (RFC 959)
echo ========================================================

set "MVN_NB=D:\6. Phan_Mem\NetBeans-22\netbeans\java\maven\bin\mvn.cmd"
if exist "%MVN_NB%" (
    set "MVN_CMD=%MVN_NB%"
) else (
    set "MVN_CMD=mvn"
)

call "%MVN_CMD%" exec:java -Dexec.mainClass="com.ftpsystem.server.ServerMain"
pause
