@echo off
setlocal

set "PROJECT_DIR=%~dp0fabric\26.1-26.2"
set "OUTPUT_DIR=%PROJECT_DIR%\build\libs"

echo.
echo ========================================
echo  Vinculum - Build for 26.1 and 26.2
echo ========================================
echo.

if not exist "%PROJECT_DIR%\gradlew.bat" (
    echo ERROR: Gradle Wrapper not found at:
    echo %PROJECT_DIR%
    set "EXIT_CODE=1"
    goto :finish
)

pushd "%PROJECT_DIR%"
call gradlew.bat clean build build26_2
set "EXIT_CODE=%ERRORLEVEL%"
popd

if not "%EXIT_CODE%"=="0" (
    echo.
    echo ERROR: Build failed with exit code %EXIT_CODE%.
    goto :finish
)

echo.
echo ========================================
echo  Build completed successfully
echo ========================================
echo.
echo Generated files:
echo %OUTPUT_DIR%
echo.
for %%F in ("%OUTPUT_DIR%\vinculum-*-26.1_26.1.2.jar" "%OUTPUT_DIR%\vinculum-*-26.2.jar") do (
    if exist "%%~fF" echo  - %%~nxF
)

:finish
echo.
if /I not "%~1"=="--no-pause" pause
exit /b %EXIT_CODE%
