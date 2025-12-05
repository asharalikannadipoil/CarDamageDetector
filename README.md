# Car Damage Detection Android App

A comprehensive Android application that uses TensorFlow Lite and AI to detect car damage from images captured via camera or selected from gallery.

## Features

### Core Functionality
- **AI-Powered Damage Detection**: Uses TensorFlow Lite for on-device car damage detection
- **Camera Integration**: Capture photos directly using CameraX with guidance overlay
- **Gallery Support**: Import and analyze existing images from device gallery
- **Real-time Analysis**: Fast on-device processing with GPU acceleration support
- **Damage Visualization**: Interactive overlays showing detected damage with bounding boxes

### Damage Detection Capabilities
- **Damage Types**: Scratches, dents, cracks, and rust detection
- **Severity Assessment**: Minor, moderate, and severe damage classification  
- **Location Mapping**: Identifies damage location on the vehicle
- **Confidence Scoring**: Provides confidence percentage for each detection

### User Interface
- **Modern Design**: Built with Jetpack Compose and Material Design 3
- **Intuitive Navigation**: Clean, easy-to-use interface
- **Results Display**: Detailed analysis results with damage visualization
- **History Management**: View and manage previous analysis results

### Data Management
- **Local Storage**: Uses Room database for secure local data storage
- **Analysis History**: Tracks all previous damage analyses
- **Export Options**: Save and share analysis results
- **Performance Metrics**: Processing time and accuracy statistics

## Technical Architecture

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Dagger Hilt
- **Database**: Room
- **ML Framework**: TensorFlow Lite
- **Camera**: CameraX
- **Image Processing**: Built-in Android graphics APIs

### Requirements
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)  
- **Model Size**: <50MB .tflite model
- **Performance**: <2s inference time target
- **Accuracy**: >90% for common damage types

### Key Components

#### ML Infrastructure
- `TensorFlowLiteHelper`: Core TensorFlow Lite integration
- `DamageDetectionService`: High-level AI service with preprocessing
- GPU acceleration support with fallback to CPU

#### Camera System
- `CameraManager`: CameraX wrapper with advanced features
- `PermissionManager`: Runtime permission handling
- Flash control, zoom, and guidance overlays

#### Data Layer
- `DamageDetectorDatabase`: Room database configuration
- `DamageResultRepository`: Data access abstraction
- `DamageResultDao`: Database operations

#### UI Components
- `DamageOverlay`: Interactive damage visualization
- Responsive screens for all device sizes
- Material Design 3 theming

## Setup Instructions

### Prerequisites
1. Android Studio Arctic Fox or later
2. Android SDK 34
3. Device/emulator with API level 21+

### Installation
1. Clone the repository
2. Open in Android Studio
3. Add your TensorFlow Lite model to `app/src/main/assets/damage_detection_model.tflite`
4. Build and run the application

### Model Integration
The app expects a TensorFlow Lite model with these specifications:
- **Input**: 640x640x3 RGB images
- **Output**: YOLO detection format
- **Classes**: scratch (0), dent (1), crack (2), rust (3)
- **Format**: Quantized .tflite file

## Project Structure

```
app/src/main/java/com/cardamage/detector/
├── data/
│   ├── database/          # Room database entities and converters
│   ├── dao/              # Database access objects
│   ├── repository/       # Repository pattern implementation
│   └── model/            # Data models and enums
├── ml/
│   ├── TensorFlowLiteHelper.kt    # Core ML inference
│   └── DamageDetectionService.kt  # ML service layer
├── camera/
│   ├── CameraManager.kt           # CameraX integration
│   └── PermissionManager.kt       # Permission handling
├── gallery/
│   └── ImagePicker.kt             # Gallery image selection
├── ui/
│   ├── screens/          # Compose UI screens
│   ├── components/       # Reusable UI components
│   ├── theme/           # Material Design theme
│   └── viewmodel/       # MVVM ViewModels
├── di/                   # Dependency injection modules
├── MainActivity.kt       # Main activity
└── DamageDetectorApplication.kt  # Application class
```

## Key Features Implementation

### AI-Powered Detection
- TensorFlow Lite integration with GPU delegate support
- Custom preprocessing pipeline for optimal model performance
- Non-maximum suppression for accurate bounding box detection
- Confidence thresholding and result filtering

### Advanced Camera
- CameraX integration with modern camera controls
- Real-time preview with damage detection guidance
- Flash control, zoom functionality
- Proper image capture and file management

### Smart UI
- Jetpack Compose modern UI framework
- Interactive damage visualization with overlays
- Responsive design for all screen sizes
- Material Design 3 principles

### Performance Optimization
- Background processing for ML inference
- Efficient image preprocessing and caching
- Memory management for large images
- GPU acceleration when available

## Testing

The project includes comprehensive testing:

### Unit Tests
- `DamageDetectionServiceTest`: ML service testing
- Model validation and error handling
- Data transformation testing

### Integration Tests  
- `DatabaseTest`: Room database operations
- End-to-end data flow testing
- Repository pattern validation

### UI Tests
- Compose UI testing framework
- User interaction testing
- Navigation flow validation

## Performance Considerations

### Model Optimization
- Quantized TensorFlow Lite models for size reduction
- GPU delegate for hardware acceleration
- Efficient preprocessing pipelines

### Memory Management
- Proper bitmap recycling and memory cleanup
- Background processing for heavy operations
- Efficient image scaling and rotation

### Battery Optimization
- Minimal background processing
- Efficient camera and GPU usage
- Smart caching strategies

## Future Enhancements

### Planned Features
- Real-time damage detection during camera preview
- Multiple vehicle type support (cars, trucks, motorcycles)
- Damage cost estimation integration
- Cloud sync and backup options
- Advanced analytics and reporting
- Multi-language support

### Technical Improvements
- Enhanced model accuracy with larger datasets
- Faster inference with model optimization
- Better damage severity assessment
- Integration with insurance APIs
- Vehicle part identification

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Model Training

For training your own car damage detection model:

1. **Dataset Requirements**:
   - Minimum 10,000 annotated car damage images
   - Balanced distribution across damage types
   - Various lighting conditions and angles
   - High-resolution images (minimum 640x640)

2. **Training Framework**:
   - Use YOLOv5 or YOLOv8 for object detection
   - Train on GPU-enabled environment
   - Implement data augmentation for robustness
   - Validate on held-out test set

3. **Model Conversion**:
   - Export to ONNX format first
   - Convert to TensorFlow Lite using tf.lite.TFLiteConverter
   - Apply post-training quantization for size optimization
   - Test on target Android devices

## Support

For issues, questions, or contributions, please open an issue on the GitHub repository.

---

**Note**: This application requires a trained TensorFlow Lite model for car damage detection. The model file should be placed in the assets folder as `damage_detection_model.tflite`.