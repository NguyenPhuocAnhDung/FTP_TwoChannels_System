@echo off
chcp 65001 > nul
echo ========================================================
echo   [BUILD] HE THONG FTP SERVER/CLIENT 2 KENH (RFC 959)
echo ========================================================

set "MVN_NB=D:\6. Phan_Mem\NetBeans-22\netbeans\java\maven\bin\mvn.cmd"
if exist "%MVN_NB%" (
    set "MVN_CMD=%MVN_NB%"
) else (
    set "MVN_CMD=mvn"
)

echo Dang bien dich du an bang Maven: %MVN_CMD%
call "%MVN_CMD%" clean compile
if %ERRORLEVEL% equ 0 (
    echo [THANH CONG] Bien dich hoan tat!
) else (
    echo [LOI] Bien dich that bai. Vui long kiem tra lai ma nguon.
)
pause
