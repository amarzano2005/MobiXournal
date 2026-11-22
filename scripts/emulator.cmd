@echo off
setlocal EnableExtensions
set "PYTHONUTF8=1"
set "SCRIPT_DIR=%~dp0"
set "TARGET=%SCRIPT_DIR%emulator.py"
set "PY_BIN="

if defined TODO_PYTHON (
    call :check_py "%TODO_PYTHON%"
    if defined PY_BIN goto :run
)

for %%C in (
    "%LOCALAPPDATA%\Python\bin\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python314\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python313\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python311\python.exe"
) do (
    if exist "%%~C" (
        call :check_py "%%~C"
        if defined PY_BIN goto :run
    )
)

for /f "delims=" %%P in ('where python python3 2^>nul') do (
    echo "%%P" | findstr /I /C:"WindowsApps" >nul
    if errorlevel 1 (
        call :check_py "%%P"
        if defined PY_BIN goto :run
    )
)

where py >nul 2>nul
if not errorlevel 1 (
    py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >nul 2>nul
    if not errorlevel 1 (
        py -3 "%TARGET%" %*
        exit /b %ERRORLEVEL%
    )
)

echo Error: Python 3.11+ not found. Install Python 3.11+ or set TODO_PYTHON. 1>&2
exit /b 1

:run
"%PY_BIN%" "%TARGET%" %*
exit /b %ERRORLEVEL%

:check_py
set "CAND=%~1"
if "%CAND%"=="" exit /b 1
"%CAND%" -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >nul 2>nul
if not errorlevel 1 set "PY_BIN=%CAND%"
exit /b 0
