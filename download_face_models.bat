@echo off
echo ========================================
echo Downloading Face Detection Models
echo ========================================
echo.

cd /d "%~dp0"

if not exist "src\main\resources\face_models" (
    echo Creating face_models directory...
    mkdir "src\main\resources\face_models"
)

cd src\main\resources\face_models

echo [1/2] Downloading deploy.prototxt...
echo URL: https://github.com/opencv/opencv/raw/master/samples/dnn/face_detector/deploy.prototxt
curl -L -o deploy.prototxt https://github.com/opencv/opencv/raw/master/samples/dnn/face_detector/deploy.prototxt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to download deploy.prototxt
    echo Please download manually from the URL above
) else (
    echo [OK] deploy.prototxt downloaded successfully
)
echo.

echo [2/2] Downloading res10_300x300_ssd_iter_140000.caffemodel...
echo URL: https://github.com/opencv/opencv_3rdparty/raw/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel
echo This file is ~10MB, please wait...
curl -L -o res10_300x300_ssd_iter_140000.caffemodel https://github.com/opencv/opencv_3rdparty/raw/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to download caffemodel
    echo Please download manually from the URL above
) else (
    echo [OK] caffemodel downloaded successfully
)
echo.

echo ========================================
echo Download Complete
echo ========================================
echo.
echo Verifying downloaded files:
dir
echo.
echo If both files are present, you can now start the application.
echo.

cd /d "%~dp0"
pause
