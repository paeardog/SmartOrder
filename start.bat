@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TOOLS=%ROOT%\.tools"
set "SERVER_POM=%ROOT%\server\pom.xml"
set "INI_FILE=%TOOLS%\my-3307.ini"
set "GRANTS_FILE=%TOOLS%\init-grants.sql"
set "DATA_DIR=%TOOLS%\mysqldata"
set "PROBE_FILE=%TOOLS%\probe.txt"
set "DB_NAME=smartorder"
set "DB_HOST=127.0.0.1"
set "DB_USER=root"
set "APP_PORT=8080"
set "WEB_PORT=5174"
set "OPEN_WEB=1"
set "MAVEN_OPTS=-Duser.language=en -Duser.country=US"

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
call :stop_app
set "STOP_RC=%ERRORLEVEL%"
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
call :prepare_db
if errorlevel 1 goto :bye_fail
call :run_checks
if errorlevel 1 goto :bye_fail
goto :bye_ok

:do_start
call :prepare_db
if errorlevel 1 goto :bye_fail
echo.
echo Starting backend, then the web dev server, then the browser.
echo Close this window or press a key to stop both (mysqld keeps running).
echo health    http://localhost:%APP_PORT%/api/v1/system/health
echo web       http://127.0.0.1:%WEB_PORT%
echo.

rem --- start backend in a separate minimized window ---
if defined RUN_ARGS (
  start "SmartOrder-Backend" /MIN cmd /c call "%MVN%" -B -f "%SERVER_POM%" spring-boot:run %RUN_ARGS%
) else (
  start "SmartOrder-Backend" /MIN cmd /c call "%MVN%" -B -f "%SERVER_POM%" spring-boot:run
)

echo backend   waiting for port %APP_PORT% ...
set "WAIT=0"
:wait_backend
ping -n 2 127.0.0.1 >nul
call :port_listening %APP_PORT%
if "%PORT_UP%"=="1" goto :backend_up
set /a WAIT+=1
if %WAIT% LSS 60 goto :wait_backend
echo backend   did not answer on %APP_PORT% within 120s, see the backend window
goto :start_web

:backend_up
echo backend   listening on %APP_PORT%

:start_web
if not "%OPEN_WEB%"=="1" goto :done_start
if not exist "%ROOT%\apps\web\package.json" goto :done_start

rem --- start web dev server unless something already listens on WEB_PORT ---
call :port_listening %WEB_PORT%
if "%PORT_UP%"=="1" goto :open_web
echo web       starting vite dev server on %WEB_PORT% ...
start "SmartOrder-Web" /MIN cmd /c "cd /d %ROOT% && pnpm dev:web"
set /a WAIT=0
:wait_web
ping -n 2 127.0.0.1 >nul
call :port_listening %WEB_PORT%
if "%PORT_UP%"=="1" goto :open_web
set /a WAIT+=1
if %WAIT% LSS 45 goto :wait_web
echo web       dev server did not answer on %WEB_PORT%, open it manually

:open_web
echo web       opening http://127.0.0.1:%WEB_PORT% in the browser
start "" "http://127.0.0.1:%WEB_PORT%"

:done_start
echo.
echo SmartOrder is up. Backend http://localhost:%APP_PORT%  Web http://127.0.0.1:%WEB_PORT%
echo.
echo Press any key to stop the backend and web windows...
pause >nul
call :stop_started
goto :bye_ok

:port_listening
set "PORT_UP=0"
netstat -ano | findstr /R "LISTENING" | findstr ":%~1 " >nul 2>&1
if not errorlevel 1 set "PORT_UP=1"
exit /b 0

:stop_started
call :kill_port %APP_PORT%
call :kill_port %WEB_PORT%
exit /b 0

:kill_port
set "KILL_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R "LISTENING" ^| findstr ":%~1 "') do if not defined KILL_PID set "KILL_PID=%%P"
if defined KILL_PID (
  echo stopping pid %KILL_PID^ on port %~1
  taskkill /t /f /pid %KILL_PID% >nul 2>&1
)
exit /b 0

:resolve_java
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :java_check
set "JAVA_EXE="
for /f "delims=" %%I in ('where java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%I"
if not defined JAVA_EXE goto :java_missing
for %%I in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpI"
if "%JAVA_HOME:~-1%"=="\" set "JAVA_HOME=%JAVA_HOME:~0,-1%"
:java_check
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version > "%PROBE_FILE%" 2>&1
set "JAVA_VER="
for /f "usebackq tokens=3" %%I in ("%PROBE_FILE%") do if not defined JAVA_VER set "JAVA_VER=%%~I"
if not defined JAVA_VER (
  echo %JAVA_EXE% -version produced no parsable output.
  exit /b 1
)
echo jdk       %JAVA_VER%
echo           %JAVA_HOME%
set "JAVA_MAJOR=%JAVA_VER%"
for /f "tokens=1 delims=.-" %%I in ("%JAVA_VER%") do set "JAVA_MAJOR=%%I"
if "%JAVA_MAJOR%"=="1" for /f "tokens=2 delims=.-" %%I in ("%JAVA_VER%") do set "JAVA_MAJOR=%%I"
echo %JAVA_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul || exit /b 0
if %JAVA_MAJOR% LSS 17 (
  echo Need JDK 17 or newer, found %JAVA_VER%
  exit /b 1
)
exit /b 0

:java_missing
echo No Java found.
echo Install JDK 17 or newer and set JAVA_HOME, then open a new terminal and rerun.
exit /b 1

:resolve_maven
set "MVN="
for /d %%D in ("%TOOLS%\apache-maven-*") do if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
if defined MVN goto :maven_ok
for /f "delims=" %%I in ('where mvn 2^>nul') do if not defined MVN set "MVN=%%I"
if defined MVN goto :maven_ok
echo No Maven found.
echo Unzip apache-maven-3.9.9-bin.zip as %TOOLS%\apache-maven-3.9.9
echo or put an existing mvn on PATH.
exit /b 1
:maven_ok
echo maven     %MVN%
exit /b 0

:resolve_mysql
set "MYSQL_HOME="
for /d %%D in ("%TOOLS%\mysql-*") do if exist "%%D\bin\mysqld.exe" set "MYSQL_HOME=%%D"
if defined MYSQL_HOME (
  set "MYSQL_BIN=%MYSQL_HOME%\bin"
  set "MANAGED_DB=1"
  echo mysql     portable, %MYSQL_HOME%
  echo db        %DB_HOST%:3307 user %DB_USER% empty password
  exit /b 0
)
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
echo Unzip mysql-8.0.40-winx64.zip as %TOOLS%\mysql-8.0.40-winx64 and rerun.
echo The script then does initialize, start, schema.sql and seed.sql by itself.
exit /b 1

:build_client_args
if not defined DB_PORT set "DB_PORT=3307"
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
start "SmartOrder MySQL %DB_PORT%" /MIN "%MYSQL_BIN%\mysqld.exe" --defaults-file="%INI_FILE%"
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
if not exist "%ROOT%\node_modules" (
  echo node      installing npm dependencies
  call npm install
  if errorlevel 1 exit /b 1
)
echo.
echo ---- schema and seed static check ----
call npm run check
if errorlevel 1 exit /b 1
echo.
echo ---- flyway baseline vs schema.sql ----
call npm run check:flyway
if errorlevel 1 exit /b 1
if not defined MANAGED_DB exit /b 0
echo.
echo ---- behavioural tests on real MySQL, rebuilds the database ----
set "MYSQL_BIN=%MYSQL_BIN%\mysql.exe"
call npm run test:db
if errorlevel 1 exit /b 1
exit /b 0

:stop_app
call :find_app_pid
if not defined APP_PID (
  echo app       nothing listening on %APP_PORT%
  exit /b 0
)
echo app       pid %APP_PID% holds port %APP_PORT%
taskkill /pid %APP_PID% >nul 2>&1
ping -n 4 127.0.0.1 >nul
call :find_app_pid
if not defined APP_PID (
  echo app       stopped
  exit /b 0
)
echo app       pid %APP_PID% ignored the request, forcing it down
taskkill /f /pid %APP_PID% >nul 2>&1
ping -n 3 127.0.0.1 >nul
call :find_app_pid
if defined APP_PID (
  echo app       pid %APP_PID% is still there, close it by hand
  exit /b 1
)
echo app       forced down
exit /b 0

:find_app_pid
netstat -ano > "%PROBE_FILE%" 2>&1
set "APP_PID="
for /f "usebackq tokens=1,2,4,5" %%A in ("%PROBE_FILE%") do (
  if "%%A"=="TCP" if "%%C"=="LISTENING" if not defined APP_PID if "%%B"=="0.0.0.0:%APP_PORT%" set "APP_PID=%%D"
  if "%%A"=="TCP" if "%%C"=="LISTENING" if not defined APP_PID if "%%B"=="[::]:%APP_PORT%" set "APP_PID=%%D"
)
del "%PROBE_FILE%" >nul 2>&1
exit /b 0

:usage
echo SmartOrder bootstrap and launcher.
echo.
echo   start.bat            prepare toolchain, database and data, run the backend,
echo                        start the web dev server and open it in the browser
echo   start.bat db         only bring the database up and make sure it is populated
echo   start.bat reset      drop every table, re-import schema.sql and seed.sql
echo   start.bat check      static check, flyway diff and the real MySQL test suite
echo   start.bat build      prepare the database, then package the jar without running it
echo   start.bat stop       stop the backend on 8080 and the portable mysqld, in that order
echo   start.bat help       this text
echo.
echo Toolchain lookup order: JAVA_HOME then PATH for the JDK; .tools\apache-maven-*
echo then PATH for Maven; .tools\mysql-* then PATH for MySQL.
echo .tools is gitignored, so a fresh clone needs the portable Maven and MySQL
echo placed there by hand, see the two download hints above.
echo.
echo start opens apps\web on http://127.0.0.1:5174. Set OPEN_WEB=0 to skip the web
echo start and the browser, or WEB_PORT to use a different vite port.
echo.
echo Environment overrides: DB_PORT, DB_USER, DB_PASS, SO_DB_URL, WEB_PORT, OPEN_WEB.
goto :eof

:bye_fail
echo.
echo bootstrap did not finish, fix the reason printed above and rerun.
endlocal
exit /b 1

:bye_ok
endlocal
exit /b 0
