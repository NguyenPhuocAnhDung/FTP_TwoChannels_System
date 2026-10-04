@echo off
chcp 65001 > nul
echo ========================================================
echo   [CLIENT] KHOI DONG FTP DESKTOP CLIENT (RFC 959)
echo ========================================================

set "MVN_NB=D:\6. Phan_Mem\NetBeans-22\netbeans\java\maven\bin\mvn.cmd"
if exist "%MVN_NB%" (
    set "MVN_CMD=%MVN_NB%"
) else (
    set "MVN_CMD=mvn"
)

call "%MVN_CMD%" exec:java -Dexec.mainClass="com.ftpsystem.client.ClientMain"
pause
