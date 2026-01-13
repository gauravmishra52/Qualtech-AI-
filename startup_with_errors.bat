@echo off
echo ================================================
echo   APPLICATION STARTUP - ERROR DETECTION
echo ================================================
echo.
echo Running diagnostics to find startup errors...
echo.
echo ================================================
echo STEP 1: Java Check
echo ================================================
java -version
if %ERRORLEVEL% NEQ 0 (
    echo [FAILED] Java not found!
    pause
    exit /b 1
)
echo.

echo ================================================
echo STEP 2: Maven Check  
echo ================================================
call mvn -version
if %ERRORLEVEL% NEQ 0 (
    echo [FAILED] Maven not found!
    echo Trying Maven wrapper...
    call mvnw.cmd -version
    if %ERRORLEVEL% NEQ 0 (
        echo [FAILED] Maven wrapper also failed!
        pause
        exit /b 1
    )
    set USE_MVN=mvnw.cmd
) else (
    set USE_MVN=mvn
)
echo.

echo ================================================
echo STEP 3: PostgreSQL Check
echo ================================================
netstat -ano | findstr :5432
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] PostgreSQL may not be running on port 5432
    echo Continue anyway? (Y/N)
    choice /C YN /N
    if %ERRORLEVEL% EQU 2 exit /b 1
)
echo.

echo ================================================
echo STEP 4: Cleaning Previous Build
echo ================================================
call %USE_MVN% clean
echo.

echo ================================================
echo STEP 5: Compiling Application
echo ================================================
echo This may take a minute...
call %USE_MVN% compile -q
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! See errors above.
    echo.
    pause
    exit /b 1
)
echo [SUCCESS] Compilation completed!
echo.

echo ================================================
echo STEP 6: Starting Application
echo ================================================
echo.
echo Watch for errors below!
echo Application will start on http://localhost:8080
echo.
echo Press Ctrl+C to stop the server when done.
echo.
echo ================================================

call %USE_MVN% spring-boot:run

echo.
echo ================================================
echo Application stopped
echo ================================================
pause
