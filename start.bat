@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TOOLS=%ROOT%\.tools"
set "SERVER_POM=%ROOT%\server\pom.xml"
set "GRANTS_FILE=%TOOLS%\init-grants.sql"
set "DATA_DIR=%TOOLS%\mysqldata"
set "DL_DIR=%TOOLS%\downloads"
set "BIN_DIR=%TOOLS%\bin"
set "PROBE_FILE=%TEMP%\smartorder-probe.txt"
set "APP_LOG=%TOOLS%\backend.log"
set "WEB_LOG=%TOOLS%\web.log"
set "H5_LOG=%TOOLS%\h5.log"
set "DB_NAME=smartorder"
set "DB_HOST=127.0.0.1"
set "DB_USER=root"
if not defined APP_PORT set "APP_PORT=8080"
if not defined WEB_PORT set "WEB_PORT=5174"
if not defined H5_PORT set "H5_PORT=5173"
if not defined OPEN_WEB set "OPEN_WEB=1"
if not defined OPEN_H5 set "OPEN_H5=1"
if not defined START_WEB set "START_WEB=1"
if not defined START_H5 set "START_H5=1"
if not defined AUTO_INSTALL set "AUTO_INSTALL=1"
if not defined BACKEND_WAIT set "BACKEND_WAIT=300"
if not defined WEB_WAIT set "WEB_WAIT=120"
if not defined H5_WAIT set "H5_WAIT=120"
if not defined MVN_VERSION set "MVN_VERSION=3.9.16"
if not defined MYSQL_VERSION set "MYSQL_VERSION=8.0.40"
if not defined NODE_VERSION set "NODE_VERSION=v22.20.0"
if not defined PNPM_VERSION set "PNPM_VERSION=10.34.9"
set "MAVEN_OPTS=-Duser.language=en -Duser.country=US"
set "COREPACK_ENABLE_DOWNLOAD_PROMPT=0"
set "CURL=%SystemRoot%\System32\curl.exe"
set "TAR=%SystemRoot%\System32\tar.exe"
set "POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "JDK_URLS=https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
set "MVN_PKG=apache-maven-%MVN_VERSION%"
rem TUNA Apache mirror first; Apache official archives are the fallback.
set "MVN_URLS=https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://dlcdn.apache.org/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip"
set "MYSQL_PKG=mysql-%MYSQL_VERSION%-winx64"
set "MYSQL_URLS=https://cdn.mysql.com/archives/mysql-8.0/%MYSQL_PKG%.zip https://dev.mysql.com/get/Downloads/MySQL-8.0/%MYSQL_PKG%.zip"
set "NODE_PKG=node-%NODE_VERSION%-win-x64"
rem TUNA Node.js mirror first; official Node.js distribution is the fallback.
set "NODE_URLS=https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/%NODE_VERSION%/%NODE_PKG%.zip https://nodejs.org/dist/%NODE_VERSION%/%NODE_PKG%.zip https://npmmirror.com/mirrors/node/%NODE_VERSION%/%NODE_PKG%.zip"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=start"
if /i "%MODE%"=="help" goto :usage
if "%MODE%"=="-h" goto :usage
if "%MODE%"=="/?" goto :usage

echo ================ SmartOrder bootstrap ================
echo project   %ROOT%
echo mode      %MODE%
echo.

if not exist "%TOOLS%" mkdir "%TOOLS%"

if /i "%MODE%"=="start" goto :need_toolchain
if /i "%MODE%"=="build" goto :need_toolchain
goto :skip_toolchain
:need_toolchain
call :resolve_java
if errorlevel 1 goto :bye_fail
call :resolve_maven
if errorlevel 1 goto :bye_fail
:skip_toolchain

call :resolve_mysql
if errorlevel 1 goto :bye_fail

if /i "%MODE%"=="stop" goto :do_stop
if /i "%MODE%"=="db" goto :do_db
if /i "%MODE%"=="reset" goto :do_reset
if /i "%MODE%"=="check" goto :do_check
if /i "%MODE%"=="build" goto :do_build
if /i "%MODE%"=="start" goto :do_start
echo unknown argument: %MODE%
echo.
goto :usage

:do_build
call :prepare_db
if errorlevel 1 goto :bye_fail
echo.
echo build     mvn -DskipTests package
call "%MVN%" -B -f "%SERVER_POM%" -DskipTests package
if errorlevel 1 goto :bye_fail
echo build     ok, jar under server\target
goto :bye_ok

:do_stop
call :build_client_args
set "STOP_RC=0"
call :stop_service app %APP_PORT%
if errorlevel 1 set "STOP_RC=1"
call :stop_service web %WEB_PORT%
if errorlevel 1 set "STOP_RC=1"
call :stop_service h5 %H5_PORT%
if errorlevel 1 set "STOP_RC=1"
call :stop_db
if errorlevel 1 set "STOP_RC=1"
if "%STOP_RC%"=="0" goto :bye_ok
goto :bye_fail

:do_db
call :prepare_db
if errorlevel 1 goto :bye_fail
goto :bye_ok

:do_reset
set "FORCE_SQL=1"
call :prepare_db
if errorlevel 1 goto :bye_fail
goto :bye_ok

:do_check
call :resolve_node
if errorlevel 1 goto :bye_fail
call :resolve_pnpm
if errorlevel 1 goto :bye_fail
call :ensure_deps
if errorlevel 1 goto :bye_fail
call :prepare_db
if errorlevel 1 goto :bye_fail
call :run_checks
if errorlevel 1 goto :bye_fail
goto :bye_ok

:do_start
call :prepare_db
if errorlevel 1 goto :bye_fail
set "BACKEND_STARTED=0"
set "WEB_STARTED=0"
set "H5_STARTED=0"
set "BACKEND_UP=0"
set "WEB_UP=0"
set "H5_UP=0"
echo.
echo Starting backend, web and h5 dev servers, then opening browsers.
echo logs      %APP_LOG%  %WEB_LOG%  %H5_LOG%
echo health    http://localhost:%APP_PORT%/api/v1/system/health
echo web       http://127.0.0.1:%WEB_PORT%
echo h5        http://127.0.0.1:%H5_PORT%
echo.

call :port_listening %APP_PORT%
if "%PORT_UP%"=="1" goto :backend_up
if defined SO_DB_URL (
  set "SPRING_DATASOURCE_URL=%SO_DB_URL%"
  set "SPRING_DATASOURCE_USERNAME=%DB_USER%"
  if defined DB_PASS set "SPRING_DATASOURCE_PASSWORD=%DB_PASS%"
)
set "BACKEND_STARTED=1"
del "%APP_LOG%" >nul 2>&1
echo backend   mvn spring-boot:run, a first run also downloads the Maven dependencies
start "" /B cmd /c "call mvn -B -f server\pom.xml spring-boot:run > .tools\backend.log 2>&1"
call :wait_for_port %APP_PORT% %BACKEND_WAIT% "%APP_LOG%"
if "%WAIT_RESULT%"=="up" goto :backend_up
call :report_stopped backend "%APP_LOG%" %APP_PORT% %BACKEND_WAIT%
goto :start_web

:backend_up
set "BACKEND_UP=1"
call :green_echo 后端启动成功！

:start_web
if not "%START_WEB%"=="1" goto :web_disabled
call :resolve_node
if errorlevel 1 goto :web_failed
call :resolve_pnpm
if errorlevel 1 goto :web_failed
call :ensure_deps
if errorlevel 1 goto :web_failed
call :port_listening %WEB_PORT%
if "%PORT_UP%"=="1" goto :open_web
set "WEB_STARTED=1"
del "%WEB_LOG%" >nul 2>&1
echo web       vite dev server on %WEB_PORT% ...
start "" /B cmd /c "call pnpm dev:web > .tools\web.log 2>&1"
call :wait_for_port %WEB_PORT% %WEB_WAIT% "%WEB_LOG%"
if "%WAIT_RESULT%"=="up" goto :open_web
call :report_stopped web "%WEB_LOG%" %WEB_PORT% %WEB_WAIT%
goto :start_h5

:open_web
set "WEB_UP=1"
call :green_echo Web 启动成功！
if not "%OPEN_WEB%"=="1" goto :start_h5
echo web       opening http://127.0.0.1:%WEB_PORT% in the browser
start "" "http://127.0.0.1:%WEB_PORT%"
goto :start_h5

:web_disabled
echo web       START_WEB=%START_WEB%, dev server and browser skipped
goto :start_h5

:web_failed
echo web       skipped, the tools it needs are not ready
goto :start_h5

:start_h5
if not "%START_H5%"=="1" goto :h5_disabled
if not exist "%ROOT%\apps\h5" goto :h5_missing
call :resolve_node
if errorlevel 1 goto :h5_failed
call :resolve_pnpm
if errorlevel 1 goto :h5_failed
call :ensure_deps
if errorlevel 1 goto :h5_failed
call :port_listening %H5_PORT%
if "%PORT_UP%"=="1" goto :open_h5
set "H5_STARTED=1"
del "%H5_LOG%" >nul 2>&1
echo h5        pnpm dev:h5 on apps\h5, port %H5_PORT% ...
start "" /B cmd /c "call pnpm dev:h5 > .tools\h5.log 2>&1"
call :wait_for_port %H5_PORT% %H5_WAIT% "%H5_LOG%"
if "%WAIT_RESULT%"=="up" goto :open_h5
call :report_stopped h5 "%H5_LOG%" %H5_PORT% %H5_WAIT%
goto :summary

:open_h5
set "H5_UP=1"
call :green_echo H5 启动成功！
if not "%OPEN_H5%"=="1" goto :summary
echo h5        opening http://127.0.0.1:%H5_PORT% in the browser
start "" "http://127.0.0.1:%H5_PORT%"
goto :summary

:h5_disabled
echo h5        START_H5=%START_H5%, dev server and browser skipped
goto :summary

:h5_missing
echo h5        skipped, folder apps\h5 was not found
goto :summary

:h5_failed
echo h5        skipped, the tools it needs are not ready
goto :summary

:summary
echo.
echo ---------------- this run ----------------
if "%BACKEND_UP%"=="1" echo backend   running, http://localhost:%APP_PORT%/api/v1/system/health
if not "%BACKEND_UP%"=="1" echo backend   not running, reason above, more in %APP_LOG%
if "%WEB_UP%"=="1" echo web       running, http://127.0.0.1:%WEB_PORT%
if "%WEB_UP%"=="0" if "%WEB_STARTED%"=="0" if not "%START_WEB%"=="1" echo web       skipped by START_WEB
if "%H5_UP%"=="1" echo h5        running, http://127.0.0.1:%H5_PORT%
if "%H5_UP%"=="0" if "%H5_STARTED%"=="0" if not "%START_H5%"=="1" echo h5        skipped by START_H5
if "%BACKEND_UP%"=="1" if "%WEB_UP%"=="1" if "%H5_UP%"=="1" call :green_echo 启动成功！后端、Web 和 H5 均已启动。
echo db        %DB_HOST%:%DB_PORT%, data in %DATA_DIR%
echo.
echo Press a key to stop what this run started, mysqld keeps running.
echo start.bat stop shuts everything down, including the portable mysqld.
pause >nul
set "PAUSED=1"
call :stop_started
if "%BACKEND_UP%"=="1" goto :bye_ok
if "%WEB_UP%"=="1" goto :bye_ok
if "%H5_UP%"=="1" goto :bye_ok
goto :bye_fail

:green_echo
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Write-Host '%*' -ForegroundColor Green"
exit /b 0

:report_stopped
echo %~1       did not answer on port %~3
if "%WAIT_RESULT%"=="error" echo %~1       exited before binding the port
if "%WAIT_RESULT%"=="timeout" echo %~1       did not come up within %~4 seconds
echo ---- %~2 ----
if not exist "%~2" (
  echo no log file, the process never got that far
  goto :report_end
)
set "TAIL_PATH=%~2"
set "TAIL_PATH=%TAIL_PATH:'=''%"
set "TAIL_LINES=0"
for /f "usebackq delims=" %%L in (`"%POWERSHELL%" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-Content -LiteralPath '%TAIL_PATH%' -Tail 25" 2^>nul`) do (
  echo   %%L
  set /a TAIL_LINES+=1
)
if "%TAIL_LINES%"=="0" echo read %~2 by hand
:report_end
exit /b 0

:wait_for_port
set "WAIT_RESULT=timeout"
set /a WAIT_LIMIT=%~2
set "WAIT_TICKS=0"
:wait_loop
ping -n 2 127.0.0.1 >nul
call :port_listening %~1
if "%PORT_UP%"=="1" goto :wait_up
if exist "%~3" call :log_has_error "%~3"
if "%LOG_ERROR%"=="1" goto :wait_error
set /a WAIT_TICKS+=1
if %WAIT_TICKS% GEQ %WAIT_LIMIT% exit /b 0
if %WAIT_TICKS%==30 echo           still waiting for port %~1
if %WAIT_TICKS%==90 echo           still waiting, dependencies are fetched only once
goto :wait_loop
:wait_up
set "WAIT_RESULT=up"
exit /b 0
:wait_error
set "WAIT_RESULT=error"
exit /b 0

:log_has_error
set "LOG_ERROR=0"
findstr /m /c:"BUILD FAILURE" /c:"APPLICATION FAILED TO START" /c:"Unable to access jar" /c:"ERR_PNPM" /c:"Missing script" /c:"ELIFECYCLE" /c:"is not recognized" /c:"Cannot find module" /c:"command not found" /c:"EADDRINUSE" "%~1" >nul 2>&1
if not errorlevel 1 set "LOG_ERROR=1"
exit /b 0

:port_listening
set "PORT_UP=0"
call :find_pid %~1
if defined FOUND_PID set "PORT_UP=1"
exit /b 0

:stop_started
if "%BACKEND_STARTED%"=="1" call :stop_service app %APP_PORT%
if "%WEB_STARTED%"=="1" call :stop_service web %WEB_PORT%
if "%H5_STARTED%"=="1" call :stop_service h5 %H5_PORT%
exit /b 0

:scan_java
for /d %%D in ("%TOOLS%\jdk-*") do if exist "%%D\bin\javac.exe" set "JAVA_HOME=%%D"
if defined JAVA_HOME exit /b 0
if exist "%JAVA_HOME%\bin\javac.exe" exit /b 0
set "JAVAC="
for /f "delims=" %%I in ('where javac 2^>nul') do if not defined JAVAC set "JAVAC=%%I"
if not defined JAVAC exit /b 0
for %%I in ("%JAVAC%") do set "JAVA_HOME=%%~dpI"
if "%JAVA_HOME:~-1%"=="\" set "JAVA_HOME=%JAVA_HOME:~0,-1%"
exit /b 0

:probe_java
set "JAVA_VER="
if not defined JAVA_HOME exit /b 1
"%JAVA_HOME%\bin\java.exe" -version > "%PROBE_FILE%" 2>&1
if not exist "%PROBE_FILE%" exit /b 1
for /f "usebackq tokens=3" %%I in ("%PROBE_FILE%") do if not defined JAVA_VER set "JAVA_VER=%%~I"
del "%PROBE_FILE%" >nul 2>&1
if not defined JAVA_VER exit /b 1
set "JAVA_MAJOR=%JAVA_VER%"
for /f "tokens=1 delims=.-" %%I in ("%JAVA_VER%") do set "JAVA_MAJOR=%%I"
if "%JAVA_MAJOR%"=="1" for /f "tokens=2 delims=.-" %%I in ("%JAVA_VER%") do set "JAVA_MAJOR=%%I"
echo %JAVA_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul || exit /b 1
if %JAVA_MAJOR% LSS 17 exit /b 1
exit /b 0

:resolve_java
call :scan_java
call :probe_java
if not errorlevel 1 goto :java_ready
if not "%AUTO_INSTALL%"=="1" goto :java_missing
echo.
echo jdk       no usable JDK 17 here, fetching one into .tools, about 190 MB
if defined JAVA_HOME echo jdk       %JAVA_VER% is what this machine has, Spring Boot 3 needs 17 or newer
call :fetch_extract jdk17.zip "%JDK_URLS%"
if errorlevel 1 goto :java_missing
set "JAVA_HOME="
call :scan_java
call :probe_java
if not errorlevel 1 goto :java_ready

:java_missing
echo.
echo Need a JDK 17 or newer that has javac in it, a browser-only Java is not enough.
echo Unzip a Windows x64 JDK 17 as %TOOLS%\jdk-17, or install one and reopen the
echo terminal, or point JAVA_HOME at it. AUTO_INSTALL=%AUTO_INSTALL% keeps downloads off.
exit /b 1

:java_ready
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo jdk       %JAVA_VER%
echo           %JAVA_HOME%
exit /b 0

:resolve_maven
set "MVN="
for /d %%D in ("%TOOLS%\apache-maven-*") do if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
if defined MVN goto :maven_ready
for /f "delims=" %%I in ('where mvn 2^>nul') do if not defined MVN (
  if /i "%%~xI"==".cmd" set "MVN=%%I"
  if /i "%%~xI"==".bat" set "MVN=%%I"
)
if defined MVN goto :maven_ready
if not "%AUTO_INSTALL%"=="1" goto :maven_missing
echo.
echo maven     not found, fetching %MVN_PKG% into .tools, about 9 MB
call :fetch_extract mvn.zip "%MVN_URLS%"
if errorlevel 1 goto :maven_missing
set "MVN="
for /d %%D in ("%TOOLS%\apache-maven-*") do if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
if defined MVN goto :maven_ready

:maven_missing
echo No usable Maven found.
echo Unzip %MVN_PKG%-bin.zip as %TOOLS%\%MVN_PKG%, or put mvn.cmd on PATH and rerun.
echo Maven 3.8 or newer works. AUTO_INSTALL=%AUTO_INSTALL% keeps downloads off.
exit /b 1

:maven_ready
for %%I in ("%MVN%") do set "PATH=%%~dpI;%PATH%"
echo maven     %MVN%
exit /b 0

:resolve_node
set "NODE_HOME="
for /d %%D in ("%TOOLS%\node-v*") do if exist "%%D\node.exe" set "NODE_HOME=%%D"
if defined NODE_HOME goto :node_probe
for /f "delims=" %%I in ('where node.exe 2^>nul') do if not defined NODE_HOME set "NODE_HOME=%%~dpI"
if not defined NODE_HOME goto :node_scan
if "%NODE_HOME:~-1%"=="\" set "NODE_HOME=%NODE_HOME:~0,-1%"
:node_scan
if defined NODE_HOME goto :node_probe
if not "%AUTO_INSTALL%"=="1" goto :node_missing
echo.
echo node      not found, fetching %NODE_PKG% into .tools, about 35 MB
call :fetch_extract node.zip "%NODE_URLS%"
if errorlevel 1 goto :node_missing
set "NODE_HOME="
for /d %%D in ("%TOOLS%\node-v*") do if exist "%%D\node.exe" set "NODE_HOME=%%D"
if defined NODE_HOME goto :node_probe

:node_missing
echo No usable Node.js found.
echo The frontend runs on vite and needs Node.js 22.12 or newer.
echo Unzip %NODE_PKG%.zip as %TOOLS%\%NODE_PKG%, or install Node and reopen the terminal.
exit /b 1

:node_probe
set "NODE_VER="
for /f "delims=" %%I in ('"%NODE_HOME%\node.exe" -v 2^>nul') do if not defined NODE_VER set "NODE_VER=%%I"
if not defined NODE_VER goto :node_missing
set "NODE_MAJOR=%NODE_VER:~1%"
for /f "tokens=1 delims=." %%I in ("%NODE_MAJOR%") do set "NODE_MAJOR=%%I"
echo %NODE_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul || goto :node_ready
if %NODE_MAJOR% LSS 22 goto :node_missing
:node_ready
set "PATH=%NODE_HOME%;%PATH%"
echo node      %NODE_VER%  %NODE_HOME%
exit /b 0

:resolve_pnpm
set "PNPM="
if exist "%BIN_DIR%\pnpm.cmd" set "PNPM=%BIN_DIR%\pnpm.cmd"
if defined PNPM goto :pnpm_ready
for /f "delims=" %%I in ('where pnpm 2^>nul') do if not defined PNPM (
  if /i "%%~xI"==".cmd" set "PNPM=%%I"
  if /i "%%~xI"==".exe" set "PNPM=%%I"
  if /i "%%~xI"==".bat" set "PNPM=%%I"
)
if defined PNPM goto :pnpm_ready
if not "%AUTO_INSTALL%"=="1" goto :pnpm_missing
echo.
echo pnpm      not found, installing pnpm %PNPM_VERSION% into .tools\bin
call :install_pnpm
if errorlevel 1 goto :pnpm_missing
set "PNPM="
if exist "%BIN_DIR%\pnpm.cmd" set "PNPM=%BIN_DIR%\pnpm.cmd"
if defined PNPM goto :pnpm_ready

:pnpm_missing
echo No pnpm found, and the automatic install left nothing behind.
echo This workspace needs pnpm %PNPM_VERSION%, install it yourself with
echo   corepack enable pnpm
echo or   npm install -g pnpm@%PNPM_VERSION%, then rerun.
exit /b 1

:pnpm_ready
for %%I in ("%PNPM%") do set "PATH=%%~dpI;%PATH%"
echo pnpm      %PNPM%
exit /b 0

:install_pnpm
set "COREPACK="
if exist "%NODE_HOME%\corepack.cmd" set "COREPACK=%NODE_HOME%\corepack.cmd"
if defined COREPACK (
  call "%COREPACK%" enable pnpm --install-directory "%BIN_DIR%" >nul 2>&1
  call "%NODE_HOME%\node.exe" "%COREPACK%" pnpm@%PNPM_VERSION% --version >nul 2>&1
  if exist "%BIN_DIR%\pnpm.cmd" exit /b 0
)
if not exist "%NODE_HOME%\npm.cmd" exit /b 1
call "%NODE_HOME%\npm.cmd" install --global --prefix "%BIN_DIR%" pnpm@%PNPM_VERSION%
if exist "%BIN_DIR%\pnpm.cmd" exit /b 0
exit /b 1

:ensure_deps
if exist "%ROOT%\node_modules\.modules.yaml" exit /b 0
echo.
echo web       installing the workspace dependencies, first run only
call "%PNPM%" install --frozen-lockfile
if not errorlevel 1 exit /b 0
echo web       the lockfile was rejected, installing from package.json instead
call "%PNPM%" install
if errorlevel 1 (
  echo The dependency install failed, read the message above.
  echo On a slow or blocked registry put an .npmrc next to package.json with
  echo   registry=https://registry.npmmirror.com
  exit /b 1
)
exit /b 0

:fetch_extract
if not exist "%CURL%" goto :fetch_missing_tools
if not exist "%TAR%" goto :fetch_missing_tools
if not exist "%DL_DIR%" mkdir "%DL_DIR%"
set "ZIP_FILE=%DL_DIR%\%~1"
call :fetch_to %2 "%ZIP_FILE%"
if errorlevel 1 exit /b 1
call :expand_zip "%ZIP_FILE%"
if errorlevel 1 exit /b 1
del "%ZIP_FILE%" >nul 2>&1
exit /b 0

:fetch_to
set "PART_FILE=%~2.part"
for %%U in (%~1) do (
  echo download  trying %%U
  "%CURL%" -L --fail --retry 2 --retry-delay 3 --connect-timeout 15 -S -o "%PART_FILE%" "%%U"
  if not errorlevel 1 if exist "%PART_FILE%" (
    move /y "%PART_FILE%" "%~2" >nul
    exit /b 0
  )
  del "%PART_FILE%" >nul 2>&1
)
echo download  none of the mirrors answered
exit /b 1

:expand_zip
"%TAR%" -xf "%~1" -C "%TOOLS%"
if not errorlevel 1 exit /b 0
echo extract   %TAR% could not open %~1
exit /b 1

:fetch_missing_tools
echo %SystemRoot%\System32\curl.exe or tar.exe is missing, this Windows is too old for
echo the automatic download. Unzip the tools into .tools by hand, see start.bat help.
exit /b 1

:resolve_mysql
set "MYSQL_HOME="
for /d %%D in ("%TOOLS%\mysql-*") do if exist "%%D\bin\mysqld.exe" set "MYSQL_HOME=%%D"
if defined MYSQL_HOME goto :mysql_portable
if not "%AUTO_INSTALL%"=="1" goto :mysql_system
echo.
echo mysql     not found, fetching %MYSQL_PKG% into .tools, about 240 MB
echo mysql     this is the big one, keep this window open
call :fetch_extract mysql.zip "%MYSQL_URLS%"
if errorlevel 1 goto :mysql_system
set "MYSQL_HOME="
for /d %%D in ("%TOOLS%\mysql-*") do if exist "%%D\bin\mysqld.exe" set "MYSQL_HOME=%%D"
if defined MYSQL_HOME goto :mysql_portable
goto :mysql_system

:mysql_portable
set "MYSQL_BIN=%MYSQL_HOME%\bin"
set "MANAGED_DB=1"
if not defined DB_PORT set "DB_PORT=3307"
echo mysql     portable, %MYSQL_HOME%
echo db        %DB_HOST%:%DB_PORT% user %DB_USER% empty password
exit /b 0

:mysql_system
set "MYSQL_BIN="
for /f "delims=" %%I in ('where mysql.exe 2^>nul') do if not defined MYSQL_BIN set "MYSQL_BIN=%%~dpI"
if not defined MYSQL_BIN goto :mysql_missing
if "%MYSQL_BIN:~-1%"=="\" set "MYSQL_BIN=%MYSQL_BIN:~0,-1%"
if not defined DB_PORT set "DB_PORT=3306"
set "MANAGED_DB="
echo mysql     system instance, this script does not start or stop it
echo db        %DB_HOST%:%DB_PORT% user %DB_USER%
echo Set SO_DB_URL to your full jdbc url if it differs from application-dev.yml.
exit /b 0

:mysql_missing
echo No MySQL found.
echo Unzip %MYSQL_PKG%.zip as %TOOLS%\%MYSQL_PKG% and rerun, the script then does
echo initialize, start, schema.sql and seed.sql by itself on port 3307.
echo A MySQL you already run works too, put mysql.exe on PATH and set SO_DB_URL.
exit /b 1

:build_client_args
if not defined DB_PORT set "DB_PORT=3307"
set "INI_FILE=%TOOLS%\my-%DB_PORT%.ini"
set "CLIENT_ARGS=--protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% --connect-timeout=5 --default-character-set=utf8mb4"
set "CLIENT_PWD="
if defined DB_PASS set "CLIENT_PWD=-p%DB_PASS%"
set "MYSQL_EXE=%MYSQL_BIN%\mysql.exe"
set "MYSQLADMIN_EXE=%MYSQL_BIN%\mysqladmin.exe"
exit /b 0

:write_ini
set "FWD_MYSQL=%MYSQL_HOME:\=/%"
set "FWD_TOOLS=%TOOLS:\=/%"
(
  echo [client]
  echo port=%DB_PORT%
  echo.
  echo [mysqld]
  echo port=%DB_PORT%
  echo basedir=%FWD_MYSQL%
  echo datadir=%FWD_TOOLS%/mysqldata
  echo character-set-server=utf8mb4
  echo collation-server=utf8mb4_unicode_ci
  echo default-time-zone='+08:00'
  echo skip-name-resolve
  echo max_connections=60
  echo innodb_buffer_pool_size=128M
  echo innodb_flush_log_at_trx_commit=2
  echo log-error=%FWD_TOOLS%/mysql.log
  echo pid-file=%FWD_TOOLS%/mysqld.pid
  echo init-file=%FWD_TOOLS%/init-grants.sql
) > "%INI_FILE%"
(
  echo CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '';
  echo GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
  echo CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '';
  echo GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
  echo FLUSH PRIVILEGES;
) > "%GRANTS_FILE%"
exit /b 0

:prepare_db
call :build_client_args
if not defined MANAGED_DB (
  "%MYSQL_EXE%" %CLIENT_ARGS% %CLIENT_PWD% -N -B -e "SELECT 1" >nul 2>&1
  if errorlevel 1 (
    echo Cannot reach %DB_HOST%:%DB_PORT% as %DB_USER%. Start that server yourself,
    echo or drop a portable MySQL into .tools so this script can manage it.
    exit /b 1
  )
  echo db        reachable at %DB_HOST%:%DB_PORT%, schema left untouched
  exit /b 0
)
call :write_ini
if not exist "%DATA_DIR%\mysql" goto :init_db
echo db        data directory ready
goto :probe_db
:init_db
echo db        initializing data directory, first run only
"%MYSQL_BIN%\mysqld.exe" --defaults-file="%INI_FILE%" --initialize-insecure
if errorlevel 1 (
  echo Init failed, see %TOOLS%\mysql.log
  exit /b 1
)
:probe_db
"%MYSQLADMIN_EXE%" --protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% ping >nul 2>&1
if not errorlevel 1 goto :db_up
echo db        not listening on %DB_PORT%, starting mysqld
start "" /B "%MYSQL_BIN%\mysqld.exe" --defaults-file="%INI_FILE%" >nul 2>&1
set "DB_TRIES=0"
:db_wait
ping -n 2 127.0.0.1 >nul
"%MYSQLADMIN_EXE%" --protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% ping >nul 2>&1
if not errorlevel 1 goto :db_up
set /a DB_TRIES+=1
if %DB_TRIES% LSS 60 goto :db_wait
echo mysqld did not become ready on %DB_HOST%:%DB_PORT% within 60 seconds.
echo If the port answers but root is refused, %GRANTS_FILE% did not run:
echo stop mysqld and rerun this script so it applies init-file again.
echo Otherwise read %TOOLS%\mysql.log
exit /b 1
:db_up
call :ensure_schema
exit /b %ERRORLEVEL%

:stop_db
if not defined MANAGED_DB (
  echo This script only stops the portable mysqld it started.
  echo Manage a system MySQL with its own service tooling.
  exit /b 0
)
"%MYSQLADMIN_EXE%" --protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% ping >nul 2>&1
if errorlevel 1 (
  echo No mysqld answering on %DB_HOST%:%DB_PORT%
  exit /b 0
)
echo db        shutting down %DB_HOST%:%DB_PORT%
"%MYSQLADMIN_EXE%" --protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% shutdown
if errorlevel 1 (
  echo Shutdown refused, check %TOOLS%\mysql.log
  exit /b 1
)
echo db        stopped
exit /b 0

:ensure_schema
call :count_tables
if not defined TABLE_COUNT (
  echo Query against %DB_HOST%:%DB_PORT% failed, check %TOOLS%\mysql.log
  exit /b 1
)
if "%FORCE_SQL%"=="1" goto :apply_sql
if not "%TABLE_COUNT%"=="0" (
  echo db        %TABLE_COUNT% tables present, import skipped
  echo db        run start.bat reset to drop and rebuild
  call :show_status
  exit /b 0
)
:apply_sql
if not exist "%ROOT%\db\schema.sql" (
  echo %ROOT%\db\schema.sql is missing, this copy of the repository is incomplete.
  exit /b 1
)
if not exist "%ROOT%\db\seed.sql" (
  echo %ROOT%\db\seed.sql is missing, this copy of the repository is incomplete.
  exit /b 1
)
echo db        importing db\schema.sql
"%MYSQL_EXE%" %CLIENT_ARGS% %CLIENT_PWD% < "%ROOT%\db\schema.sql"
if errorlevel 1 (
  echo schema.sql failed
  exit /b 1
)
echo db        importing db\seed.sql
"%MYSQL_EXE%" %CLIENT_ARGS% %CLIENT_PWD% < "%ROOT%\db\seed.sql"
if errorlevel 1 (
  echo seed.sql failed
  exit /b 1
)
call :count_tables
echo db        %TABLE_COUNT% tables built
call :show_status
exit /b 0

:count_tables
set "TABLE_COUNT="
"%MYSQL_EXE%" %CLIENT_ARGS% %CLIENT_PWD% -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB_NAME%'" > "%PROBE_FILE%" 2>nul
if errorlevel 1 exit /b 0
for /f "usebackq delims=" %%I in ("%PROBE_FILE%") do set "TABLE_COUNT=%%I"
del "%PROBE_FILE%" >nul 2>&1
exit /b 0

:show_status
echo tz        %DB_HOST%:%DB_PORT% session time zone, table count and order count
"%MYSQL_EXE%" %CLIENT_ARGS% %CLIENT_PWD% -N -B -e "SELECT @@session.time_zone, @@transaction_isolation, (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB_NAME%'), (SELECT COUNT(*) FROM %DB_NAME%.o_order)"
exit /b 0

:run_checks
echo.
echo ---- schema and seed static check ----
call "%PNPM%" run check
if errorlevel 1 exit /b 1
echo.
echo ---- flyway baseline vs schema.sql ----
call "%PNPM%" run check:flyway
if errorlevel 1 exit /b 1
if not defined MANAGED_DB exit /b 0
echo.
echo ---- behavioural tests on real MySQL, rebuilds the database ----
set "MYSQL_BIN=%MYSQL_EXE%"
echo client    %MYSQL_BIN%
call "%PNPM%" run test:db
if errorlevel 1 exit /b 1
exit /b 0

:stop_service
call :find_pid %2
if not defined FOUND_PID (
  echo %~1       nothing listening on port %2
  exit /b 0
)
echo %~1       pid %FOUND_PID% holds port %2
taskkill /pid %FOUND_PID% >nul 2>&1
ping -n 4 127.0.0.1 >nul
call :find_pid %2
if not defined FOUND_PID (
  echo %~1       stopped
  exit /b 0
)
echo %~1       pid %FOUND_PID% ignored the request, forcing the process tree down
taskkill /t /f /pid %FOUND_PID% >nul 2>&1
ping -n 3 127.0.0.1 >nul
call :find_pid %2
if defined FOUND_PID (
  echo %~1       pid %FOUND_PID% is still there, close it by hand
  exit /b 1
)
echo %~1       forced down
exit /b 0

:find_pid
set "FOUND_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%~1 .*LISTENING"') do if not defined FOUND_PID set "FOUND_PID=%%P"
exit /b 0

:usage
echo SmartOrder bootstrap and launcher.
echo.
echo   start.bat            fetch whatever is missing, prepare the database and the
echo                        data, run the backend, start the web dev server, open it
echo   start.bat db         only bring the database up and make sure it is populated
echo   start.bat reset      drop every table, re-import schema.sql and seed.sql
echo   start.bat check      static check, flyway diff and the real MySQL test suite
echo   start.bat build      prepare the database, then package the jar without running it
echo   start.bat stop       stop backend, web, H5 and the portable mysqld
echo   start.bat help       this text
echo.
echo A machine with none of the tools downloads about 470 MB into .tools on the first
echo run, for JDK 17, Maven, MySQL and Node.js, and then the Maven and pnpm
echo dependencies on top of that. Keep the window open while it works.
echo AUTO_INSTALL=0 keeps every download off and only prints what to do by hand.
echo.
echo Toolchain lookup order: .tools\jdk-* then JAVA_HOME then PATH javac;
echo .tools\apache-maven-* then PATH for Maven; .tools\node-v* then PATH for Node.js;
echo .tools\bin then PATH for pnpm; .tools\mysql-* then PATH for MySQL.
echo .tools is gitignored, so nothing found there comes with the repository.
echo.
echo start runs apps\web and apps\h5, then opens both in the browser.
echo Web: pnpm dev:web, H5: pnpm dev:h5.
echo Set OPEN_WEB=0 or OPEN_H5=0 to keep the respective browser closed;
echo START_WEB=0 or START_H5=0 skips the respective dev server.
echo Ports default to WEB_PORT=5174 and H5_PORT=5173.
echo Logs: .tools\backend.log, .tools\web.log and .tools\h5.log.
echo BACKEND_WAIT, WEB_WAIT and H5_WAIT control startup timeouts in seconds.
echo.
echo Environment overrides: AUTO_INSTALL, BACKEND_WAIT, WEB_WAIT, H5_WAIT, DB_PORT, DB_USER,
echo DB_PASS, SO_DB_URL, WEB_PORT, H5_PORT, OPEN_WEB, OPEN_H5, START_WEB, START_H5,
echo MVN_VERSION, MYSQL_VERSION,
echo NODE_VERSION, PNPM_VERSION.
goto :eof

:bye_fail
echo.
echo bootstrap did not finish, fix the reason printed above and rerun.
if not "%PAUSED%"=="1" pause
endlocal
exit /b 1

:bye_ok
endlocal
exit /b 0
