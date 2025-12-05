# Car Damage Detection Model Training Guide

## Overview

This guide explains how to train a production-ready car damage detection model for the Android app. The app is currently configured with a minimal demo model that can be replaced with a trained model.

## Current Status

✅ **Android App**: Fully functional with complete infrastructure  
✅ **Model Infrastructure**: TensorFlow Lite integration ready  
✅ **Training Scripts**: Complete training pipeline available  
✅ **Demo Model**: Basic model integrated for testing  
🔄 **Production Model**: Requires training with real data  

## Quick Start (Demo Model)

The app is already configured with a minimal model that demonstrates the complete integration:

```bash
# The demo model is already integrated
ls app/src/main/assets/damage_detection_model.tflite

# You can now build and test the Android app
```

## Production Model Training

To create a production-ready model, follow these steps:

### Prerequisites

1. **Python Environment** (3.8+)
2. **GPU Support** (recommended for training)
3. **Dependencies**:
   ```bash
   pip install ultralytics torch torchvision tensorflow opencv-python
   pip install Pillow numpy matplotlib albumentations onnx
   ```

### Step 1: Generate Synthetic Dataset

```bash
cd model_training/scripts
python generate_synthetic_dataset.py --num_images 10000 --output_dir ../data/car_damage_dataset
```

**Parameters**:
- `--num_images`: Number of synthetic images (5000-20000 recommended)
- `--output_dir`: Output directory for dataset

**Output**:
- Training images with YOLO format annotations
- Validation split (80/20)
- Dataset configuration file

### Step 2: Train YOLOv8 Model

```bash
python train_yolov8.py \
  --dataset ../data/car_damage_dataset \
  --model_size n \
  --epochs 100 \
  --batch_size 16
```

**Parameters**:
- `--model_size`: `n` (nano) for mobile, `s` (small) for better accuracy
- `--epochs`: Training epochs (100-200 recommended)
- `--batch_size`: Adjust based on GPU memory

**Training Time**: 2-6 hours depending on hardware

### Step 3: Convert to TensorFlow Lite

```bash
python convert_to_tflite.py \
  --onnx_path runs/train/best.onnx \
  --output_dir ../models/
```

**Output**:
- `damage_detection_model.tflite`: Optimized for Android
- Multiple quantization versions (dynamic, float16, int8)
- Conversion summary and performance metrics

### Step 4: Full Pipeline (Automated)

Run the complete pipeline with one command:

```bash
python full_training_pipeline.py \
  --num_images 10000 \
  --model_size n \
  --epochs 100 \
  --android_app_path ../../
```

This will:
1. Generate synthetic dataset
2. Train YOLOv8 model
3. Convert to TensorFlow Lite
4. Integrate with Android app automatically

## Model Specifications

The Android app expects models with these exact specifications:

### Input
- **Format**: RGB images
- **Size**: 640×640 pixels
- **Type**: Float32, normalized [0,1]
- **Shape**: `[1, 640, 640, 3]`

### Output
- **Format**: YOLO detection format
- **Shape**: `[1, 25200, 9]`
- **Structure**: `[x, y, w, h, confidence, class0, class1, class2, class3]`

### Classes
- `0`: Scratch
- `1`: Dent
- `2`: Crack
- `3`: Rust

### Performance Targets
- **Size**: <50MB for mobile deployment
- **Inference**: <2 seconds on mid-range Android devices
- **Accuracy**: >85% mAP@0.5 on validation set

## Data Requirements

For production models, consider:

### Real Data Collection
- **Minimum**: 5,000 annotated car damage images
- **Recommended**: 20,000+ images
- **Diversity**: Various lighting, angles, vehicle types
- **Quality**: High resolution (>640px), clear damage visibility

### Annotation Format
- YOLO format: `class x_center y_center width height`
- Normalized coordinates (0-1)
- Accurate bounding boxes around damage areas

### Data Sources
- Insurance company databases
- Automotive repair shops
- Synthetic data augmentation
- Public datasets (rare for car damage)

## Advanced Training Options

### Custom Model Architecture

Modify `train_yolov8.py` for custom architectures:

```python
# Use larger model for better accuracy
model_size = 's'  # or 'm', 'l', 'x'

# Custom hyperparameters
train_config.update({
    'lr0': 0.001,      # Learning rate
    'batch': 32,       # Larger batch size
    'epochs': 200,     # More training
    'patience': 50,    # Early stopping
})
```

### Transfer Learning

Start from pre-trained weights:

```python
# Load pre-trained model
model = YOLO('yolov8n.pt')  # Pre-trained on COCO

# Fine-tune on car damage data
results = model.train(
    data='car_damage_dataset/dataset.yaml',
    epochs=100,
    imgsz=640
)
```

### Data Augmentation

Enhance training with custom augmentations:

```python
# In generate_synthetic_dataset.py
augmentation_pipeline = A.Compose([
    A.RandomBrightnessContrast(p=0.8),
    A.HueSaturationValue(p=0.7),
    A.GaussNoise(p=0.5),
    A.MotionBlur(p=0.3),
    A.RandomShadow(p=0.4),
    A.RandomRain(p=0.3),
])
```

## Model Optimization

### Quantization Options

1. **Dynamic Quantization** (Recommended)
   - Best compatibility
   - ~50% size reduction
   - Minimal accuracy loss

2. **Float16 Quantization**
   - Good performance/size balance
   - ~50% size reduction
   - ARM GPU optimization

3. **INT8 Quantization**
   - Maximum size reduction
   - Requires representative dataset
   - Potential accuracy loss

### Mobile-Specific Optimizations

```python
# TensorFlow Lite conversion options
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]

# GPU delegate support
converter.allow_custom_ops = False
converter.experimental_new_converter = True
```

## Integration with Android App

### Automatic Integration

The training pipeline automatically copies the model:

```bash
# Model is copied to app/src/main/assets/damage_detection_model.tflite
# Placeholder file is removed
# App is ready to build
```

### Manual Integration

If needed, manually copy the model:

```bash
cp trained_model.tflite app/src/main/assets/damage_detection_model.tflite
```

### Verification

The Android app includes validation:

```kotlin
// Model loading validation
private fun initializeModel() {
    try {
        val model = loadModelFile()
        interpreter = Interpreter(model, options)
        Log.d(TAG, "Model loaded successfully")
    } catch (e: Exception) {
        Log.e(TAG, "Error initializing model: ${e.message}")
    }
}
```

## Performance Monitoring

### Training Metrics

Monitor these during training:
- **mAP@0.5**: Mean Average Precision
- **Precision**: True positives / (True positives + False positives)
- **Recall**: True positives / (True positives + False negatives)
- **Loss**: Training convergence

### Validation Metrics

Test on held-out data:
- **Inference Time**: <2s target on mobile
- **Model Size**: <50MB target
- **Accuracy**: >85% mAP@0.5 target
- **False Positive Rate**: <10% target

### Mobile Performance

Test on actual Android devices:
- **Loading Time**: Model initialization speed
- **Inference Speed**: Per-image processing time
- **Memory Usage**: RAM consumption
- **Battery Impact**: Power efficiency

## Troubleshooting

### Common Issues

1. **Model Loading Fails**
   ```
   Error: Model not compatible
   Solution: Check input/output shapes match exactly
   ```

2. **Poor Accuracy**
   ```
   Issue: Low mAP scores
   Solution: More training data, longer training, data augmentation
   ```

3. **Slow Inference**
   ```
   Issue: >5s inference time
   Solution: Use smaller model (nano), quantization, GPU optimization
   ```

4. **Large Model Size**
   ```
   Issue: >50MB model
   Solution: INT8 quantization, model pruning, smaller architecture
   ```

### Debugging Tools

```python
# Model analysis
from ultralytics import YOLO
model = YOLO('best.pt')
model.info()  # Model summary
model.val()   # Validation metrics

# TensorFlow Lite analysis
import tensorflow as tf
interpreter = tf.lite.Interpreter('model.tflite')
print(interpreter.get_input_details())
print(interpreter.get_output_details())
```

## Production Deployment

### Model Validation Checklist

- [ ] Input shape: `[1, 640, 640, 3]`
- [ ] Output shape: `[1, 25200, 9]`
- [ ] Model size: <50MB
- [ ] Inference time: <2s on target devices
- [ ] Accuracy: >85% mAP@0.5
- [ ] Android integration: Successful loading
- [ ] GPU delegate: Compatibility verified

### Deployment Steps

1. **Train Production Model**
   ```bash
   python full_training_pipeline.py --num_images 20000 --epochs 200
   ```

2. **Validate Performance**
   ```bash
   python validate_model.py --model_path best.tflite --test_images validation/
   ```

3. **Build Android App**
   ```bash
   cd android_app
   ./gradlew assembleDebug
   ```

4. **Test on Devices**
   - Install APK on test devices
   - Test with various car images
   - Monitor performance metrics

5. **Release**
   - Create release build
   - Upload to Play Store or distribute internally

## Next Steps

1. **Collect Real Data**: Replace synthetic data with real car damage images
2. **Improve Accuracy**: Experiment with larger models and better data
3. **Optimize Performance**: Fine-tune for specific Android devices
4. **Add Features**: Severity assessment, cost estimation, reporting
5. **Scale Deployment**: Cloud training, automated retraining, A/B testing

## Support

For issues or questions:
- Check the Android app logs for model loading errors
- Validate model format with TensorFlow Lite tools
- Test inference with sample images
- Review training metrics for convergence issues

The complete infrastructure is ready - you just need to train with your specific data and requirements!