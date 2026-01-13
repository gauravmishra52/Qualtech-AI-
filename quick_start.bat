@echo off
COLOR 0A
echo ============================================
echo    QUICK START - QualTech AI Application
echo ============================================
echo.

REM Step 1: Check PostgreSQL
echo [STEP 1/3] Checking if PostgreSQL is running...
echo.
net start | findstr /i "postgres" > nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] PostgreSQL is running!
) else (
    echo [ERROR] PostgreSQL is NOT running!
    echo.
    echo Please start PostgreSQL from Windows Services or run:
    echo    Services -^> Find "postgresql" -^> Right-click -^> Start
    echo.
    echo Press any key to exit...
    pause > nul
    exit /b 1
)
echo.

REM Step 2: Download models (optional prompt)
echo [STEP 2/3] Checking for face detection models...
echo.
if exist "src\main\resources\face_models\deploy.prototxt" (
    if exist "src\main\resources\face_models\res10_300x300_ssd_iter_140000.caffemodel" (
        echo [OK] Face detection models found!
    ) else (
        echo [WARNING] Caffemodel is missing
        echo Face recognition will not work until you download it.
        echo Run: download_face_models.bat
    )
) else (
    echo [WARNING] Models are missing
    echo Face recognition will not work until you download them.
    echo Run: download_face_models.bat
)
echo.
echo Note: Application will start anyway, face features just won't work yet.
echo.

REM Step 3: Start the application
echo [STEP 3/3] Starting the application...
echo.
echo This will take 30-60 seconds. Watch for:
echo   - Spring Boot banner
echo   - "Started QualtechAiApplication in X seconds"
echo.
echo Then open your browser to: http://localhost:8080
echo.
echo ============================================
echo   Starting now... Please wait...
echo ============================================
echo.

call mvn spring-boot:run

echo.
echo ============================================
echo Application stopped
echo ============================================
pause
