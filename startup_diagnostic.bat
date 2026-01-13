@echo off
echo ========================================
echo QualTech-AI Application Diagnostic Tool
echo ========================================
echo.

echo [1/6] Checking Java Installation...
echo ----------------------------------------
java -version 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java is not installed or not in PATH
    echo Please install Java 21 and add it to your PATH
    goto :error
) else (
    echo [OK] Java is installed
)
echo.

echo [2/6] Checking Maven Installation...
echo ----------------------------------------
call mvn -version 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven is not installed or not in PATH
    echo Please install Maven and add it to your PATH
    goto :error
) else (
    echo [OK] Maven is installed
)
echo.

echo [3/6] Checking PostgreSQL Service...
echo ----------------------------------------
netstat -an | findstr :5432 > nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] PostgreSQL is running on port 5432
) else (
    echo [WARNING] PostgreSQL does not appear to be running
    echo Please start PostgreSQL service from Windows Services
)
echo.

echo [4/6] Checking Port 8080 Availability...
echo ----------------------------------------
netstat -an | findstr :8080 | findstr LISTENING > nul
if %ERRORLEVEL% EQU 0 (
    echo [WARNING] Port 8080 is already in use
    echo Another application might be running on port 8080
    netstat -ano | findstr :8080
) else (
    echo [OK] Port 8080 is available
)
echo.

echo [5/6] Checking Face Recognition Models...
echo ----------------------------------------
if exist "src\main\resources\face_models\deploy.prototxt" (
    echo [OK] deploy.prototxt found
) else (
    echo [WARNING] deploy.prototxt NOT FOUND
    echo Expected location: src\main\resources\face_models\deploy.prototxt
)

if exist "src\main\resources\face_models\res10_300x300_ssd_iter_140000.caffemodel" (
    echo [OK] caffemodel found
) else (
    echo [WARNING] caffemodel NOT FOUND
    echo Expected location: src\main\resources\face_models\res10_300x300_ssd_iter_140000.caffemodel
)
echo.

echo [6/6] Checking Project Structure...
echo ----------------------------------------
if exist "pom.xml" (
    echo [OK] pom.xml found
) else (
    echo [ERROR] pom.xml NOT FOUND - Are you in the project directory?
    goto :error
)

if exist "src\main\resources\application.yml" (
    echo [OK] application.yml found
) else (
    echo [ERROR] application.yml NOT FOUND
    goto :error
)
echo.

echo ========================================
echo Diagnostic Summary
echo ========================================
echo.
echo If all checks passed, you can try starting the application with:
echo   mvn clean compile
echo   mvn spring-boot:run
echo.
echo If warnings were shown:
echo   - Install PostgreSQL and start the service
echo   - Download face recognition models or disable face recognition
echo   - Free up port 8080 if it's in use
echo.
goto :end

:error
echo.
echo ========================================
echo ERRORS FOUND - Please fix the above issues
echo ========================================
exit /b 1

:end
pause
