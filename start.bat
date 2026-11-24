@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TOOLS=%ROOT%\.tools"
set "SERVER_POM=%ROOT%\server\pom.xml"
set "MIGRATION_DIR=%ROOT%\server\src\main\resources\db\migration"
set "GRANTS_FILE=%TOOLS%\init-grants.sql"
set "DATA_DIR=%TOOLS%\mysqldata"
set "DL_DIR=%TOOLS%\downloads"
set "BIN_DIR=%TOOLS%\bin"
set "PROBE_FILE=%TEMP%\smartorder-probe.txt"
set "APP_LOG=%TOOLS%\backend.log"
set "WEB_LOG=%TOOLS%\web.log"
set "H5_LOG=%TOOLS%\h5.log"
set "CONSOLE_LOG=%TOOLS%\console.log"
set "DB_NAME=smartorder"
set "DB_HOST=127.0.0.1"
set "DB_USER=root"
if not defined APP_PORT set "APP_PORT=8080"
if not defined WEB_PORT set "WEB_PORT=5174"
if not defined H5_PORT set "H5_PORT=5173"
if not defined CONSOLE_PORT set "CONSOLE_PORT=5175"
if not defined OPEN_WEB set "OPEN_WEB=1"
if not defined OPEN_H5 set "OPEN_H5=1"
if not defined OPEN_CONSOLE set "OPEN_CONSOLE=1"
if not defined START_WEB set "START_WEB=1"
if not defined START_H5 set "START_H5=1"
if not defined START_CONSOLE set "START_CONSOLE=1"
if not defined AUTO_INSTALL set "AUTO_INSTALL=1"
if not defined BACKEND_WAIT set "BACKEND_WAIT=300"
if not defined WEB_WAIT set "WEB_WAIT=120"
if not defined H5_WAIT set "H5_WAIT=120"
if not defined CONSOLE_WAIT set "CONSOLE_WAIT=120"
if not defined MVN_VERSION set "MVN_VERSION=3.9.16"
if not defined MYSQL_VERSION set "MYSQL_VERSION=8.0.40"
if not defined NODE_VERSION set "NODE_VERSION=v22.20.0"
if not defined PNPM_VERSION set "PNPM_VERSION=10.34.9"
set "MAVEN_OPTS=-Duser.language=en -Duser.country=US"
set "COREPACK_ENABLE_DOWNLOAD_PROMPT=0"
set "CURL=%SystemRoot%\System32\curl.exe"
set "TAR=%SystemRoot%\System32\tar.exe"
set "POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
rem 有些公司网络连不上证书吊销服务器，schannel 会直接报错，这里放宽成能查就查。
set "CURL_TLS="
"%CURL%" --ssl-revoke-best-effort --version >nul 2>&1
if not errorlevel 1 set "CURL_TLS=--ssl-revoke-best-effort"
if not defined CURL_TLS (
  "%CURL%" --ssl-no-revoke --version >nul 2>&1
  if not errorlevel 1 set "CURL_TLS=--ssl-no-revoke"
)
rem 下载顺序：先清华大学镜像，失败再自动换官方源，不需要手动切换。
set "JDK_PKG=OpenJDK17U-jdk_x64_windows_hotspot_17.0.21.1_2"
set "JDK_URLS=https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/%JDK_PKG%.zip https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip https://mirrors.aliyun.com/adoptium/17/jdk/x64/windows/%JDK_PKG%.zip https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
rem 清华走不通时先用华为、阿里的 Adoptium 同步，再回落到微软和 Adoptium 官方。
set "MVN_PKG=apache-maven-%MVN_VERSION%"
rem 清华的 apache 镜像在前，Apache 官方 dlcdn 和 archive 兜底。
set "MVN_URLS=https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://mirrors.aliyun.com/apache/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://mirrors.huaweicloud.com/apache/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://dlcdn.apache.org/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/%MVN_PKG%-bin.zip"
set "MYSQL_PKG=mysql-%MYSQL_VERSION%-winx64"
rem 清华、阿里、华为、中科大等国内镜像都没有 MySQL 的 Windows 版，只能走官方 CDN。
set "MYSQL_URLS=https://cdn.mysql.com/archives/mysql-8.0/%MYSQL_PKG%.zip https://dev.mysql.com/get/Downloads/MySQL-8.0/%MYSQL_PKG%.zip"
set "NODE_PKG=node-%NODE_VERSION%-win-x64"
rem 清华的 nodejs-release 镜像在前，npmmirror 同步的 nodejs 镜像其次，Node.js 官方最后。
set "NODE_URLS=https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/%NODE_VERSION%/%NODE_PKG%.zip https://npmmirror.com/mirrors/node/%NODE_VERSION%/%NODE_PKG%.zip https://nodejs.org/dist/%NODE_VERSION%/%NODE_PKG%.zip"
rem 前端依赖和 pnpm 用的国内 npm 镜像，失败后自动回到官方 registry。
set "NPM_REGISTRY=https://registry.npmmirror.com"
rem Maven 依赖的国内仓库，只替换 Maven Central，服务器上没有快照的包仍然去 Central 取。
set "MVN_MIRROR=https://maven.aliyun.com/repository/public"
set "M2_SETTINGS=%TOOLS%\m2-settings.xml"
set "MAVEN_DIST=%TOOLS%\%MVN_PKG%"
set "MVN_ARGS="
if not defined MAVEN_USER_SETTINGS set "MAVEN_USER_SETTINGS=SHARED"

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
echo status    checking Java and Maven
call :resolve_java
if errorlevel 1 goto :bye_fail
call :resolve_maven
if errorlevel 1 goto :bye_fail
:skip_toolchain

echo status    checking MySQL
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
call :stop_service console %CONSOLE_PORT%
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
echo status    preparing the database
call :prepare_db
if errorlevel 1 goto :bye_fail
set "BACKEND_STARTED=0"
set "WEB_STARTED=0"
set "H5_STARTED=0"
set "CONSOLE_STARTED=0"
set "BACKEND_UP=0"
set "WEB_UP=0"
set "H5_UP=0"
set "CONSOLE_UP=0"
call :resolve_lan_ip
echo.
echo status    starting backend, web, h5 and console, the first run downloads dependencies
echo [提示] 第一次运行会下载 Maven 和 pnpm 依赖，后台在做事，不是卡住了。
echo        进度和错误都写在 .tools 目录下的日志里，启动完成后会打印访问地址。
echo logs      %APP_LOG%  %WEB_LOG%  %H5_LOG%  %CONSOLE_LOG%
echo.

echo status    checking and starting the backend
call :port_listening %APP_PORT%
if "%PORT_UP%"=="1" goto :backend_up
if defined DB_PASS set "SPRING_DATASOURCE_PASSWORD=%DB_PASS%"
if defined SO_DB_URL (
  set "SPRING_DATASOURCE_URL=%SO_DB_URL%"
  set "SPRING_DATASOURCE_USERNAME=%DB_USER%"
)
set "BACKEND_STARTED=1"
del "%APP_LOG%" >nul 2>&1
echo backend   mvn spring-boot:run, a first run also downloads the Maven dependencies
echo [提示] 后端正在启动，首次下载 Maven 依赖可能要好几分钟，日志在 .tools\backend.log。
echo        依赖默认从国内仓库下载，第一次拉完会缓存起来，以后启动就不用再下了。
echo        等待期间每 20 秒会显示一行后端日志，看到“正在下载依赖”就是还在拉 Maven 依赖。
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c call %MVN% -B -f server\pom.xml spring-boot:run > .tools\backend.log 2>&1' -WorkingDirectory '%ROOT%' -WindowStyle Hidden"
call :wait_for_port %APP_PORT% %BACKEND_WAIT% "%APP_LOG%"
if "%WAIT_RESULT%"=="up" goto :backend_up
call :report_stopped backend "%APP_LOG%" %APP_PORT% %BACKEND_WAIT%
goto :start_web

:backend_up
set "BACKEND_UP=1"
call :green_echo backend started

:start_web
echo status    checking and starting web, h5 and console
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
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c call pnpm dev:web > .tools\web.log 2>&1' -WorkingDirectory '%ROOT%' -WindowStyle Hidden"
call :wait_for_port %WEB_PORT% %WEB_WAIT% "%WEB_LOG%"
if "%WAIT_RESULT%"=="up" goto :open_web
call :report_stopped web "%WEB_LOG%" %WEB_PORT% %WEB_WAIT%
goto :start_h5

:open_web
set "WEB_UP=1"
call :green_echo web started
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
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c call pnpm dev:h5 > .tools\h5.log 2>&1' -WorkingDirectory '%ROOT%' -WindowStyle Hidden"
call :wait_for_port %H5_PORT% %H5_WAIT% "%H5_LOG%"
if "%WAIT_RESULT%"=="up" goto :open_h5
call :report_stopped h5 "%H5_LOG%" %H5_PORT% %H5_WAIT%
goto :start_console

:open_h5
set "H5_UP=1"
call :green_echo h5 started
if not "%OPEN_H5%"=="1" goto :start_console
echo h5        opening http://127.0.0.1:%H5_PORT% in the browser
start "" "http://127.0.0.1:%H5_PORT%"
goto :start_console

:h5_disabled
echo h5        START_H5=%START_H5%, dev server and browser skipped
goto :start_console

:h5_missing
echo h5        skipped, folder apps\h5 was not found
goto :start_console

:h5_failed
echo h5        skipped, the tools it needs are not ready
goto :start_console

:start_console
if not "%START_CONSOLE%"=="1" goto :console_disabled
if not exist "%ROOT%\apps\console" goto :console_missing
call :resolve_node
if errorlevel 1 goto :console_failed
call :resolve_pnpm
if errorlevel 1 goto :console_failed
call :ensure_deps
if errorlevel 1 goto :console_failed
call :port_listening %CONSOLE_PORT%
if "%PORT_UP%"=="1" goto :open_console
set "CONSOLE_STARTED=1"
del "%CONSOLE_LOG%" >nul 2>&1
echo console   pnpm dev:console on apps\console, port %CONSOLE_PORT% ...
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c call pnpm dev:console > .tools\console.log 2>&1' -WorkingDirectory '%ROOT%' -WindowStyle Hidden"
call :wait_for_port %CONSOLE_PORT% %CONSOLE_WAIT% "%CONSOLE_LOG%"
if "%WAIT_RESULT%"=="up" goto :open_console
call :report_stopped console "%CONSOLE_LOG%" %CONSOLE_PORT% %CONSOLE_WAIT%
goto :summary

:open_console
set "CONSOLE_UP=1"
call :green_echo console started
if not "%OPEN_CONSOLE%"=="1" goto :summary
echo console   opening http://127.0.0.1:%CONSOLE_PORT% in the browser
start "" "http://127.0.0.1:%CONSOLE_PORT%"
goto :summary

:console_disabled
echo console   START_CONSOLE=%START_CONSOLE%, dev server and browser skipped
goto :summary

:console_missing
echo console   skipped, folder apps\console was not found
goto :summary

:console_failed
echo console   skipped, the tools it needs are not ready
goto :summary

:summary
echo.
echo ---------------- this run ----------------
if "%BACKEND_UP%"=="1" echo backend   running, http://localhost:%APP_PORT%/api/v1/system/health
if not "%BACKEND_UP%"=="1" echo backend   not running, reason above, more in %APP_LOG%
if "%WEB_UP%"=="1" echo web       running, http://127.0.0.1:%WEB_PORT%
if "%WEB_UP%"=="0" if "%WEB_STARTED%"=="1" echo web       failed to start, see %WEB_LOG%
if "%WEB_UP%"=="0" if "%START_WEB%"=="1" if not "%WEB_STARTED%"=="1" echo web       not running, reason above
if "%WEB_UP%"=="0" if not "%START_WEB%"=="1" echo web       skipped by START_WEB
if "%H5_UP%"=="1" echo h5        running, http://127.0.0.1:%H5_PORT%
if "%H5_UP%"=="0" if "%H5_STARTED%"=="1" echo h5        failed to start, see %H5_LOG%
if "%H5_UP%"=="0" if "%START_H5%"=="1" if not "%H5_STARTED%"=="1" echo h5        not running, reason above
if "%H5_UP%"=="0" if not "%START_H5%"=="1" echo h5        skipped by START_H5
if "%CONSOLE_UP%"=="1" echo console   running, http://127.0.0.1:%CONSOLE_PORT%
if "%CONSOLE_UP%"=="0" if "%CONSOLE_STARTED%"=="1" echo console   failed to start, see %CONSOLE_LOG%
if "%CONSOLE_UP%"=="0" if "%START_CONSOLE%"=="1" if not "%CONSOLE_STARTED%"=="1" echo console   not running, reason above
if "%CONSOLE_UP%"=="0" if not "%START_CONSOLE%"=="1" echo console   skipped by START_CONSOLE
set "RUN_OK=1"
if not "%BACKEND_UP%"=="1" set "RUN_OK=0"
if "%START_WEB%"=="1" if not "%WEB_UP%"=="1" set "RUN_OK=0"
if "%START_H5%"=="1" if not "%H5_UP%"=="1" set "RUN_OK=0"
if "%START_CONSOLE%"=="1" if not "%CONSOLE_UP%"=="1" set "RUN_OK=0"
if "%RUN_OK%"=="1" call :green_echo all set, backend and the enabled dev servers are ready
if not "%RUN_OK%"=="1" echo status    startup incomplete, see the state above and the logs
echo db        %DB_HOST%:%DB_PORT%, data in %DATA_DIR%
call :print_access
echo.
if not "%RUN_OK%"=="1" goto :startup_incomplete
echo The servers keep running in the background after this window closes.
echo Run start.bat stop to shut the backend, the dev servers and mysqld down.
echo Press any key to close this window.
pause >nul
goto :bye_ok

:startup_incomplete
echo Startup was incomplete, press any key to stop what this run started and close.
pause >nul
set "PAUSED=1"
call :stop_started
goto :bye_fail

:green_echo
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Write-Host '%*' -ForegroundColor Green"
exit /b 0

:resolve_lan_ip
set "LAN_IP="
set "LAN_IP_FILE=%TEMP%\smartorder-lan.txt"
del "%LAN_IP_FILE%" >nul 2>nul
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | Sort-Object { $_.RouteMetric + $_.InterfaceMetric } | Select-Object -First 1 | ForEach-Object { Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $_.ifIndex -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty IPAddress }" > "%LAN_IP_FILE%" 2>nul
if exist "%LAN_IP_FILE%" set /p LAN_IP=<"%LAN_IP_FILE%"
del "%LAN_IP_FILE%" >nul 2>nul
if not defined LAN_IP for /f "tokens=2 delims=:" %%I in ('ipconfig ^| findstr /c:"IPv4"') do if not defined LAN_IP set "LAN_IP=%%I"
if defined LAN_IP for /f "tokens=* delims= " %%I in ("%LAN_IP%") do set "LAN_IP=%%I"
exit /b 0

:print_access
echo.
echo ---------------- access ----------------
if "%BACKEND_UP%%WEB_UP%%H5_UP%%CONSOLE_UP%"=="0000" echo address   no service answered, nothing to open
if "%BACKEND_UP%"=="1" echo health    http://localhost:%APP_PORT%/api/v1/system/health
if "%WEB_UP%"=="1" echo local     web     http://127.0.0.1:%WEB_PORT%
if "%H5_UP%"=="1" echo           h5      http://127.0.0.1:%H5_PORT%
if "%CONSOLE_UP%"=="1" echo           console http://127.0.0.1:%CONSOLE_PORT%
if defined LAN_IP if "%WEB_UP%"=="1" echo lan       web     http://%LAN_IP%:%WEB_PORT%
if defined LAN_IP if "%H5_UP%"=="1" echo           h5      http://%LAN_IP%:%H5_PORT%
if defined LAN_IP if "%CONSOLE_UP%"=="1" echo           console http://%LAN_IP%:%CONSOLE_PORT%
if not defined LAN_IP echo lan       not detected, run ipconfig and use the IPv4 address
echo note      the dev servers listen on 0.0.0.0, so other devices use the lan address;
echo note      0.0.0.0 is the listen address, not an address to open from another machine.
echo note      if a device cannot connect, allow these ports in Windows Firewall.
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
set "TAIL_LINES=0"
del "%PROBE_FILE%" >nul 2>&1
"%POWERSHELL%" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-Content -LiteralPath $env:TAIL_PATH -Tail 25" > "%PROBE_FILE%" 2>nul
if exist "%PROBE_FILE%" for /f "usebackq delims=" %%L in ("%PROBE_FILE%") do (
  echo   %%L
  set /a TAIL_LINES+=1
)
del "%PROBE_FILE%" >nul 2>&1
if "%TAIL_LINES%"=="0" echo read %~2 by hand
:report_end
exit /b 0

:show_log_progress
if not exist "%~1" exit /b 0
set "PROGRESS_PATH=%~1"
set "PROGRESS_HINT=%~2"
del "%PROBE_FILE%" >nul 2>&1
"%POWERSHELL%" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$t = Get-Content -LiteralPath $env:PROGRESS_PATH -Tail 6 -ErrorAction SilentlyContinue; if (-not $t) { exit 0 }; $map = @{ 'Downloading from' = '正在下载依赖'; 'Downloaded from' = '下载完成'; 'BUILD SUCCESS' = '后端构建完成'; 'BUILD FAILURE' = '构建失败'; 'APPLICATION FAILED TO START' = '启动失败'; 'Started ' = '后端已启动'; 'Tomcat started' = '后端已启动'; 'ERR_PNPM' = '依赖安装出错'; 'is not recognized' = '命令无法识别' }; $hits = @(); foreach ($line in $t) { foreach ($key in $map.Keys) { if ($line.Contains($key)) { if ($hits -notcontains $map[$key]) { $hits += $map[$key] }; break } } }; if ($hits.Count -gt 0) { Write-Output ('[进度] ' + $env:PROGRESS_HINT + '：' + ($hits -join '；')) } else { $last = ('') + $t[-1]; if ($last.Length -gt 70) { $last = $last.Substring($last.Length - 70) }; Write-Output ('[进度] ' + $env:PROGRESS_HINT + '：' + $last) }" > "%PROBE_FILE%" 2>nul
if not exist "%PROBE_FILE%" exit /b 0
for /f "usebackq delims=" %%L in ("%PROBE_FILE%") do echo %%L
del "%PROBE_FILE%" >nul 2>&1
exit /b 0

:wait_for_port
set "WAIT_RESULT=timeout"
set /a WAIT_LIMIT=%~2
set "WAIT_TICKS=0"
set "WAIT_INDEX=0"
set "WAIT_DETAIL_TICKS=0"
:wait_loop
ping -n 2 127.0.0.1 >nul
call :port_listening %~1
if "%PORT_UP%"=="1" goto :wait_up
if exist "%~3" call :log_has_error "%~3"
if "%LOG_ERROR%"=="1" goto :wait_error
set /a WAIT_TICKS+=1
if %WAIT_TICKS% GEQ %WAIT_LIMIT% exit /b 0
set /a WAIT_ELAPSED=WAIT_TICKS*2
set /a WAIT_BUCKET=WAIT_TICKS/30
set /a WAIT_REMAINDER=WAIT_TICKS-WAIT_BUCKET*30
set /a WAIT_INDEX+=1
set /a WAIT_DETAIL=WAIT_INDEX/10
set /a WAIT_DETAIL_TICKS=WAIT_DETAIL*10
if %WAIT_DETAIL_TICKS%==%WAIT_INDEX% goto :wait_progress
if %WAIT_REMAINDER%==0 echo           waiting for port %~1, %WAIT_ELAPSED%s elapsed
goto :wait_loop
:wait_progress
if not exist "%~3" goto :wait_loop
if "%WAIT_INDEX%"=="10" echo           waiting for port %~1, downloading or starting, this can take minutes
call :show_log_progress "%~3" "已等 %WAIT_ELAPSED% 秒"
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
if "%CONSOLE_STARTED%"=="1" call :stop_service console %CONSOLE_PORT%
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
echo [下载] Java 运行环境 JDK 17，本机没有找到可用的版本。
echo        安装包约 190 MB，会下载到 .tools 目录，第一次运行需要等几分钟。
echo        默认使用清华大学镜像，连不上会自动换华为云、阿里云，最后才用官方源。
if defined JAVA_HOME echo [提示] 本机现有 Java 版本是 %JAVA_VER%，本项目需要 17 或更高版本。
call :fetch_extract jdk17.zip "%JDK_URLS%"
if errorlevel 1 goto :java_missing
set "JAVA_HOME="
call :scan_java
call :probe_java
if not errorlevel 1 goto :java_ready

:java_missing
echo.
echo 没有找到带 javac 的 JDK 17 或更高版本，只有浏览器里的 Java 是不够的。
echo 自动下载没有成功时，可以手动解压一个 Windows x64 JDK 17 到 %TOOLS%\jdk-17，
echo 或者安装 JDK 后设置 JAVA_HOME 指向它，再重新运行 start.bat。
echo 设置 AUTO_INSTALL=0 可以完全关闭自动下载。
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
echo [下载] Maven 构建工具 %MVN_PKG%，安装包约 9 MB。
echo        默认使用清华大学镜像，失败会自动换阿里云、华为云，最后才用 Apache 官方源。
call :fetch_extract mvn.zip "%MVN_URLS%"
if errorlevel 1 goto :maven_missing
set "MVN="
for /d %%D in ("%TOOLS%\apache-maven-*") do if exist "%%D\bin\mvn.cmd" set "MVN=%%D\bin\mvn.cmd"
if defined MVN goto :maven_ready

:maven_missing
echo 没有找到可用的 Maven。
echo 可以手动解压 %MVN_PKG%-bin.zip 到 %TOOLS%\%MVN_PKG%，或者把 mvn.cmd 放进 PATH 后重试。
echo Maven 3.8 或更高版本都可以。设置 AUTO_INSTALL=0 可以关闭自动下载。
exit /b 1

:maven_ready
for %%I in ("%MVN%") do set "PATH=%%~dpI;%PATH%"
set "MVN_IS_PORTABLE=0"
for %%I in ("%MVN%") do if /i "%%~dpI"=="%MAVEN_DIST%\bin\" set "MVN_IS_PORTABLE=1"
call :prepare_maven_mirror
if defined MVN_ARGS set "MVN=%MVN% %MVN_ARGS%"
echo maven     %MVN%
exit /b 0

:prepare_maven_mirror
rem .tools 里自己下载的 Maven 用生成的 settings.xml 覆盖用户设置；
rem PATH 里现成的 Maven 默认叠在用户 settings 之上，改成 STANDALONE 就只用生成的配置。
if not "%MVN_IS_PORTABLE%"=="1" if not "%MAVEN_USER_SETTINGS%"=="STANDALONE" exit /b 0
if not exist "%M2_SETTINGS%" goto :write_maven_mirror
findstr /c:"%MVN_MIRROR%" "%M2_SETTINGS%" >nul 2>&1
if not errorlevel 1 goto :maven_mirror_ready
:write_maven_mirror
if not exist "%TOOLS%" mkdir "%TOOLS%"
> "%M2_SETTINGS%" (
  echo ^<?xml version="1.0" encoding="UTF-8"?^>
  echo ^<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  echo   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  echo   xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"^>
  echo   ^<mirrors^>
  echo     ^<mirror^>
  echo       ^<id^>smartorder-fast^</id^>
  echo       ^<name^>domestic mirror for maven central^</name^>
  echo       ^<url^>%MVN_MIRROR%^</url^>
  echo       ^<mirrorOf^>central^</mirrorOf^>
  echo     ^</mirror^>
  echo   ^</mirrors^>
  echo ^</settings^>
)
set "MVN_ARGS=-s "%M2_SETTINGS%""
if /i "%MAVEN_USER_SETTINGS%"=="STANDALONE" set "MVN_ARGS=%MVN_ARGS% -gs "%M2_SETTINGS%""
:maven_mirror_ready
echo [加速] Maven 依赖从国内仓库 %MVN_MIRROR% 下载，比官方 Central 快很多。
echo        Maven 自己会记住哪些地址不通，下次直接走能用的那个，不用手动设置。
echo        想完全用官方仓库，设置 MAVEN_USER_SETTINGS=SHARED 即可。
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
echo [下载] Node.js 运行环境 %NODE_VERSION%，安装包约 35 MB。
echo        默认使用清华大学镜像，失败会自动换 npmmirror，最后才用 Node.js 官方源。
call :fetch_extract node.zip "%NODE_URLS%"
if errorlevel 1 goto :node_missing
set "NODE_HOME="
for /d %%D in ("%TOOLS%\node-v*") do if exist "%%D\node.exe" set "NODE_HOME=%%D"
if defined NODE_HOME goto :node_probe

:node_missing
echo 没有找到可用的 Node.js。
echo 前端用 vite 构建，需要 Node.js 22.12 或更高版本。
echo 可以手动解压 %NODE_PKG%.zip 到 %TOOLS%\%NODE_PKG%，或者安装 Node.js 后重新打开终端。
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
echo [下载] 没有找到 pnpm，正在安装 pnpm %PNPM_VERSION% 到 .tools\bin。
echo        默认使用国内 npm 镜像，失败会自动改用官方源，文件很小，很快就好。
call :install_pnpm
if errorlevel 1 goto :pnpm_missing
set "PNPM="
if exist "%BIN_DIR%\pnpm.cmd" set "PNPM=%BIN_DIR%\pnpm.cmd"
if defined PNPM goto :pnpm_ready

:pnpm_missing
echo 没有找到可用的 pnpm，自动安装也没有成功。
echo 本项目需要 pnpm %PNPM_VERSION%，可以手动执行：
echo   corepack enable pnpm
echo 或   npm install -g pnpm@%PNPM_VERSION%
echo 装好后重新运行 start.bat。
exit /b 1

:pnpm_ready
for %%I in ("%PNPM%") do set "PATH=%%~dpI;%PATH%"
echo pnpm      %PNPM%
exit /b 0

:install_pnpm
set "COREPACK_NPM_REGISTRY=%NPM_REGISTRY%"
set "COREPACK="
if exist "%NODE_HOME%\corepack.cmd" set "COREPACK=%NODE_HOME%\corepack.cmd"
if defined COREPACK (
  call "%COREPACK%" enable pnpm --install-directory "%BIN_DIR%" >nul 2>&1
  call "%NODE_HOME%\node.exe" "%COREPACK%" pnpm@%PNPM_VERSION% --version >nul 2>&1
  if exist "%BIN_DIR%\pnpm.cmd" exit /b 0
)
if not exist "%NODE_HOME%\npm.cmd" exit /b 1
call "%NODE_HOME%\npm.cmd" install --global --prefix "%BIN_DIR%" --no-fund --no-audit --registry=%NPM_REGISTRY% pnpm@%PNPM_VERSION%
if exist "%BIN_DIR%\pnpm.cmd" exit /b 0
echo [下载] 国内镜像没有装好 pnpm，改用官方源重试。
call "%NODE_HOME%\npm.cmd" install --global --prefix "%BIN_DIR%" --no-fund --no-audit pnpm@%PNPM_VERSION%
if exist "%BIN_DIR%\pnpm.cmd" exit /b 0
exit /b 1

:ensure_deps
set "DEPS_STAMP=%TOOLS%\deps.sha256"
set "STAMPED="
set "LOCK_HASH="
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%ROOT%\pnpm-lock.yaml" SHA256 2^>nul') do if not defined LOCK_HASH set "LOCK_HASH=%%H"
if not defined LOCK_HASH goto :deps_install
if not exist "%ROOT%\node_modules\.modules.yaml" goto :deps_install
if not exist "%DEPS_STAMP%" goto :deps_install
set /p "STAMPED=" < "%DEPS_STAMP%"
if not "%STAMPED%"=="%LOCK_HASH%" goto :deps_install
exit /b 0
:deps_install
echo.
echo [依赖] 正在安装前端依赖，第一次运行需要几分钟，请耐心等待。
echo        默认使用国内镜像 %NPM_REGISTRY%，失败会自动改用官方源。
call "%PNPM%" install --frozen-lockfile --registry=%NPM_REGISTRY%
if not errorlevel 1 goto :deps_ok
echo [依赖] 国内镜像这一步没有成功，改用官方源再试。
call "%PNPM%" install --frozen-lockfile
if not errorlevel 1 goto :deps_ok
echo [依赖] 依赖清单和锁文件对不上，改为按 package.json 重新解析。
call "%PNPM%" install --registry=%NPM_REGISTRY%
if not errorlevel 1 goto :deps_ok
echo [依赖] 国内镜像装不上，最后用官方源再试一次。
call "%PNPM%" install
if errorlevel 1 goto :deps_failed
:deps_ok
if defined LOCK_HASH >"%DEPS_STAMP%" echo %LOCK_HASH%
echo [依赖] 安装完成。
exit /b 0

:deps_failed
echo [依赖失败] 前端依赖没有装完，请检查网络后重新运行 start.bat。
echo            公司网络需要代理时，先设置 HTTP_PROXY 和 HTTPS_PROXY 环境变量。
echo            也可以在本目录的 package.json 旁放一个 .npmrc，写入：
echo              registry=https://registry.npmmirror.com
exit /b 1

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
if not defined FETCH_SPEED_GIVE_UP set "FETCH_SPEED_GIVE_UP=8"
set "PART_FILE=%~2.part"
for %%U in (%~1) do (
  call :announce_url "%%U"
  "%CURL%" %CURL_TLS% -L --fail --retry 1 --retry-delay 3 --connect-timeout 15 --speed-limit 1024 --speed-time %FETCH_SPEED_GIVE_UP% -S --progress-bar -o "%PART_FILE%" "%%U"
  if not errorlevel 1 if exist "%PART_FILE%" (
    move /y "%PART_FILE%" "%~2" >nul
    echo [下载] 完成。
    exit /b 0
  )
  del "%PART_FILE%" >nul 2>&1
  echo [下载] 这个来源没有成功，自动换下一个来源...
  echo        某条线路要是一直没有数据，十几秒后会自动放弃，不用一直等。
)
echo [下载失败] 所有来源都没有下载成功，请检查网络后重新运行。
echo            也可以手动下载对应的压缩包，放进 %DL_DIR% 再重试。
exit /b 1

:announce_url
set "URL_SOURCE=官方源"
echo %~1| findstr /i /c:"tuna.tsinghua.edu.cn" >nul
if not errorlevel 1 set "URL_SOURCE=清华大学镜像"
echo %~1| findstr /i /c:"npmmirror.com" >nul
if not errorlevel 1 set "URL_SOURCE=国内镜像 npmmirror"
echo %~1| findstr /i /c:"aliyun.com" >nul
if not errorlevel 1 set "URL_SOURCE=阿里云镜像"
echo %~1| findstr /i /c:"huaweicloud.com" >nul
if not errorlevel 1 set "URL_SOURCE=华为云镜像"
echo %~1| findstr /i /c:"ustc.edu.cn" >nul
if not errorlevel 1 set "URL_SOURCE=中科大镜像"
echo.
echo [下载] 来源：%URL_SOURCE%
echo        地址：%~1
echo        进度条走完之前请不要关闭窗口，这个来源失败会自动换下一个。
exit /b 0

:expand_zip
echo [安装] 正在解压 %~nx1 到 .tools 目录，请稍候...
"%TAR%" -xf "%~1" -C "%TOOLS%"
if not errorlevel 1 (
  echo [安装] 解压完成。
  exit /b 0
)
echo [安装失败] 压缩包打不开，可能没有下载完整，请重新运行 start.bat。
exit /b 1

:fetch_missing_tools
echo 系统里缺少 curl.exe 或 tar.exe，无法自动下载。
echo 这个 Windows 版本太旧，请手动把工具解压到 .tools 目录，具体见 start.bat help。
exit /b 1

:resolve_mysql
set "MYSQL_HOME="
for /d %%D in ("%TOOLS%\mysql-*") do if exist "%%D\bin\mysqld.exe" set "MYSQL_HOME=%%D"
if defined MYSQL_HOME goto :mysql_portable
if not "%AUTO_INSTALL%"=="1" goto :mysql_system
echo.
echo [下载] MySQL 数据库 %MYSQL_VERSION%，压缩包约 240 MB，是最大的一项。
echo        清华大学镜像站不提供 Windows 版 MySQL，这一步从 MySQL 官方 CDN 下载。
echo        下载时间会比较长，进度条走完前请不要关闭窗口；中途断了会自动重试。
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
if not defined DB_PASS set "DB_PASS=123456"
echo mysql     portable, %MYSQL_HOME%
echo db        %DB_HOST%:%DB_PORT% user %DB_USER%, password 123456 unless DB_PASS says otherwise
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
echo 没有找到可用的 MySQL。
echo 可以手动解压 %MYSQL_PKG%.zip 到 %TOOLS%\%MYSQL_PKG%，然后重新运行 start.bat，
echo 脚本会自动完成初始化、启动并导入 schema.sql 和 seed.sql，端口 3307。
echo 如果你自己已经装了 MySQL，把 mysql.exe 放进 PATH 并设置 SO_DB_URL 也可以。
exit /b 1

:build_client_args
if not defined DB_PORT set "DB_PORT=3307"
set "INI_FILE=%TOOLS%\my-%DB_PORT%.ini"
set "CLIENT_ARGS=--protocol=tcp -h %DB_HOST% -P %DB_PORT% -u %DB_USER% --connect-timeout=5 --default-character-set=utf8mb4"
if defined DB_PASS set "MYSQL_PWD=%DB_PASS%"
set "MYSQL_EXE=%MYSQL_BIN%\mysql.exe"
set "MYSQLADMIN_EXE=%MYSQL_BIN%\mysqladmin.exe"
exit /b 0

:write_ini
set "FWD_MYSQL=%MYSQL_HOME%"
for %%I in ("%MYSQL_HOME%") do set "FWD_MYSQL=%%~sI"
set "FWD_MYSQL=%FWD_MYSQL:\=/%"
set "FWD_TOOLS=%TOOLS%"
for %%I in ("%TOOLS%") do set "FWD_TOOLS=%%~sI"
set "FWD_TOOLS=%FWD_TOOLS:\=/%"
(
  echo [client]
  echo port=%DB_PORT%
  echo password=%DB_PASS%
  echo.
  echo [mysqld]
  echo port=%DB_PORT%
  echo basedir="%FWD_MYSQL%"
  echo datadir="%FWD_TOOLS%/mysqldata"
  echo character-set-server=utf8mb4
  echo collation-server=utf8mb4_unicode_ci
  echo default-time-zone='+08:00'
  echo skip-name-resolve
  echo max_connections=60
  echo innodb_buffer_pool_size=128M
  echo innodb_flush_log_at_trx_commit=2
  echo log-error="%FWD_TOOLS%/mysql.log"
  echo pid-file="%FWD_TOOLS%/mysqld.pid"
  echo init-file="%FWD_TOOLS%/init-grants.sql"
) > "%INI_FILE%"
"%POWERSHELL%" -NoProfile -NonInteractive -Command "[IO.File]::WriteAllText($env:INI_FILE, [IO.File]::ReadAllText($env:INI_FILE, [Text.Encoding]::UTF8), [Text.Encoding]::Default)"
(
  echo CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '%DB_PASS%';
  echo ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '%DB_PASS%';
  echo GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
  echo CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '%DB_PASS%';
  echo ALTER USER 'root'@'localhost' IDENTIFIED BY '%DB_PASS%';
  echo GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
  echo FLUSH PRIVILEGES;
) > "%GRANTS_FILE%"
exit /b 0

:prepare_db
call :build_client_args
if not defined MANAGED_DB (
  "%MYSQL_EXE%" %CLIENT_ARGS% -N -B -e "SELECT 1" >nul 2>&1
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
set "MYSQLD_EXE=%MYSQL_BIN%\mysqld.exe"
set "MYSQL_INI=%INI_FILE%"
for %%I in ("%INI_FILE%") do set "MYSQL_INI=%%~sI"
"%POWERSHELL%" -NoProfile -NonInteractive -Command "Start-Process -FilePath $env:MYSQLD_EXE -ArgumentList ('--defaults-file=' + [char]34 + $env:MYSQL_INI + [char]34) -WindowStyle Hidden"
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
echo db        dropping flyway history, the rebuilt tables carry no migration state
"%MYSQL_EXE%" %CLIENT_ARGS% -e "DROP TABLE IF EXISTS `%DB_NAME%`.`flyway_schema_history`"
if errorlevel 1 (
  echo Could not drop %DB_NAME%.flyway_schema_history, stop the backend and run this again.
  exit /b 1
)
echo db        importing db\schema.sql
"%MYSQL_EXE%" %CLIENT_ARGS% < "%ROOT%\db\schema.sql"
if errorlevel 1 (
  echo schema.sql failed, the database is now incomplete.
  echo Fix db\schema.sql and run start.bat reset again, every table it creates is dropped first.
  exit /b 1
)
echo db        importing db\seed.sql
"%MYSQL_EXE%" %CLIENT_ARGS% < "%ROOT%\db\seed.sql"
if errorlevel 1 (
  echo seed.sql failed, the database has structure but no usable rows.
  exit /b 1
)
echo db        re-applying migrations, seed.sql alone leaves no passwords or coupons
call :apply_migrations
if errorlevel 1 exit /b 1
call :count_tables
echo db        %TABLE_COUNT% tables built
call :show_status
exit /b 0

:apply_migrations
if not exist "%MIGRATION_DIR%" (
  echo %MIGRATION_DIR% is missing, this copy of the repository is incomplete.
  exit /b 1
)
for %%F in ("%MIGRATION_DIR%\V*.sql") do (
  echo db        applying %%~nxF
  "%MYSQL_EXE%" %CLIENT_ARGS% %DB_NAME% < "%%F"
  if errorlevel 1 (
    echo %%~nxF failed, the seed is incomplete without it.
    echo Flyway applies the same file again on backend start, it is written to be re-runnable.
    exit /b 1
  )
)
exit /b 0

:count_tables
set "TABLE_COUNT="
"%MYSQL_EXE%" %CLIENT_ARGS% -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB_NAME%'" > "%PROBE_FILE%" 2>nul
if errorlevel 1 exit /b 0
for /f "usebackq delims=" %%I in ("%PROBE_FILE%") do set "TABLE_COUNT=%%I"
del "%PROBE_FILE%" >nul 2>&1
exit /b 0

:show_status
echo tz        %DB_HOST%:%DB_PORT% session time zone, table count and order count
"%MYSQL_EXE%" %CLIENT_ARGS% -N -B -e "SELECT @@session.time_zone, @@transaction_isolation, (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB_NAME%'), (SELECT COUNT(*) FROM %DB_NAME%.o_order)"
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
echo                        data, run the backend, start the web, H5 and console dev
echo                        servers, open the pages, then wait for a key; closing the
echo                        window leaves all servers running in the background
echo   start.bat db         only bring the database up and make sure it is populated
echo   start.bat reset      drop every table, re-import schema.sql, seed.sql and the migrations
echo   start.bat check      static check, flyway diff and the real MySQL test suite
echo   start.bat build      prepare the database, then package the jar without running it
echo   start.bat stop       stop backend, web, H5, console and the portable mysqld
echo   start.bat help       this text
echo.
echo A machine with none of the tools downloads about 470 MB into .tools on the first
echo run, for JDK 17, Maven, MySQL and Node.js, and then the Maven and pnpm
echo dependencies on top of that. Keep the window open while it works.
echo AUTO_INSTALL=0 keeps every download off and only prints what to do by hand.
echo Downloads try the Tsinghua mirror first, then Aliyun and Huawei Cloud, and the
echo official source last; MySQL only ships the Windows zip on its own CDN. A source
echo that sends nothing for FETCH_SPEED_GIVE_UP seconds, 8 by default, is dropped and
echo the next one is tried, so a half-open connection cannot stall the setup.
echo Maven dependencies come from the Aliyun repository through .tools\m2-settings.xml,
echo and Maven falls back to Central by itself for whatever the mirror does not have;
echo MAVEN_USER_SETTINGS=SHARED leaves an existing Maven alone, STANDALONE means the
echo generated settings are the only ones used.
echo Frontend dependencies install from the npmmirror registry first, then the
echo official npm registry, so a blocked npmjs.org does not stop the setup.
echo.
echo Toolchain lookup order: .tools\jdk-* then JAVA_HOME then PATH javac;
echo .tools\apache-maven-* then PATH for Maven; .tools\node-v* then PATH for Node.js;
echo .tools\bin then PATH for pnpm; .tools\mysql-* then PATH for MySQL.
echo .tools is gitignored, so nothing found there comes with the repository.
echo.
echo start runs apps\web, apps\h5 and apps\console, then opens all three.
echo Web: pnpm dev:web, H5: pnpm dev:h5, console: pnpm dev:console.
echo Set OPEN_WEB=0, OPEN_H5=0 or OPEN_CONSOLE=0 to keep a browser closed;
echo START_WEB=0, START_H5=0 or START_CONSOLE=0 skips the respective dev server.
echo Ports default to WEB_PORT=5174, H5_PORT=5173 and CONSOLE_PORT=5175.
echo The three dev servers listen on 0.0.0.0. The addresses are printed after the
echo servers were started, with this machine's LAN address for phones and other
echo computers. start.bat stop shuts the backend, the dev servers and mysqld down.
echo Logs: .tools\backend.log, .tools\web.log, .tools\h5.log and .tools\console.log.
echo BACKEND_WAIT, WEB_WAIT, H5_WAIT and CONSOLE_WAIT control startup timeouts in seconds.
echo.
echo Environment overrides: AUTO_INSTALL, BACKEND_WAIT, WEB_WAIT, H5_WAIT, CONSOLE_WAIT,
echo DB_PORT, DB_USER, DB_PASS, SO_DB_URL, WEB_PORT, H5_PORT, CONSOLE_PORT,
echo OPEN_WEB, OPEN_H5, OPEN_CONSOLE, START_WEB, START_H5, START_CONSOLE,
echo MVN_VERSION, MYSQL_VERSION, FETCH_SPEED_GIVE_UP, MAVEN_USER_SETTINGS,
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
