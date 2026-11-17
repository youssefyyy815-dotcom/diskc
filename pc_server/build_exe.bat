@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   DeskLink Windows EXE Builder (PyInstaller)
echo ============================================
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python 3 is not installed or not in PATH.
    echo Install Python 3 from https://www.python.org/downloads/
    pause
    exit /b 1
)

python -m pip show pyinstaller >nul 2>&1
if errorlevel 1 (
    echo Installing PyInstaller...
    python -m pip install --upgrade pyinstaller
    if errorlevel 1 (
        echo [ERROR] Failed to install PyInstaller.
        pause
        exit /b 1
    )
)

echo Building standalone DeskLinkServer.exe...
python -m PyInstaller --noconfirm --onefile --console --name DeskLinkServer pc_remote_server.py
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Built: "%~dp0dist\DeskLinkServer.exe"
echo Double-click dist\DeskLinkServer.exe to start the server!
pause
