# Face Detection Models

This directory contains the required model files for face detection and recognition.

## Required Files

### 1. deploy.prototxt

- **Description**: Caffe model architecture definition for SSD face detector
- **Download URL**: <https://github.com/opencv/opencv/raw/master/samples/dnn/face_detector/deploy.prototxt>
- **File Size**: ~28 KB
- **Location**: Place in this directory (`src/main/resources/face_models/`)

### 2. res10_300x300_ssd_iter_140000.caffemodel

- **Description**: Pre-trained weights for the SSD face detector (300x300 resolution)
- **Download URL**: <https://github.com/opencv/opencv_3rdparty/raw/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel>
- **File Size**: ~10.1 MB
- **Location**: Place in this directory (`src/main/resources/face_models/`)

## Download Instructions

### Option 1: Direct Download (Recommended)

**Windows PowerShell:**

```powershell
# Navigate to the project directory
cd c:\Users\gaura\qualtech-ai\src\main\resources\face_models

# Download deploy.prototxt
Invoke-WebRequest -Uri "https://github.com/opencv/opencv/raw/master/samples/dnn/face_detector/deploy.prototxt" -OutFile "deploy.prototxt"

# Download caffemodel
Invoke-WebRequest -Uri "https://github.com/opencv/opencv_3rdparty/raw/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel" -OutFile "res10_300x300_ssd_iter_140000.caffemodel"
```

**Windows Command Prompt (using curl):**

```cmd
cd c:\Users\gaura\qualtech-ai\src\main\resources\face_models

curl -L -o deploy.prototxt https://github.com/opencv/opencv/raw/master/samples/dnn/face_detector/deploy.prototxt

curl -L -o res10_300x300_ssd_iter_140000.caffemodel https://github.com/opencv/opencv_3rdparty/raw/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel
```

### Option 2: Manual Download

1. Click on each URL above in your browser
2. Save the files to: `c:\Users\gaura\qualtech-ai\src\main\resources\face_models\`
3. Ensure the filenames are exactly as shown

## Verification

After downloading, verify the files exist:

```cmd
dir c:\Users\gaura\qualtech-ai\src\main\resources\face_models
```

You should see:

- `deploy.prototxt` (~28 KB)
- `res10_300x300_ssd_iter_140000.caffemodel` (~10.1 MB)
- `README.md` (this file)

## Model Information

- **Model Type**: Single Shot Detector (SSD) with ResNet-10 backbone
- **Input Size**: 300x300 pixels
- **Framework**: Caffe
- **Purpose**: Face detection in images
- **Accuracy**: Good balance between speed and accuracy
- **Source**: OpenCV DNN samples

## Troubleshooting

**If downloads fail:**

- Try using a VPN if GitHub is blocked
- Download from alternative mirrors
- Check your internet connection

**If the application still can't find the models:**

- Verify the filenames are exactly correct (case-sensitive on some systems)
- Ensure files are in the correct directory
- Restart the application after adding the files
