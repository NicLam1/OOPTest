# Passport Photo Maker

A professional web application for creating standardized passport and ID photos that meet international requirements.

## Features

### 🧱 Core Functionality

- **Photo Upload & Preview:** Upload JPEG or PNG files and preview them directly in the app
- **Automatic Background Removal:** Uses background removal algorithm in Java to isolate the subject
- **Manual Cropping & Resizing:** Manually crop photos and automatically resize them to meet official ID photo dimensions
- **High-Resolution Export:** Save processed ID photos in high quality (PNG or JPEG)
- **International Format Support:** Select from globally recognized ID photo sizes:
  - 35x45 mm (UK, Europe, Australia, Singapore, Nigeria)
  - 2x2 inches (US and India)
  - 5x7 cm (Canada)
  - 33x48 mm (China)

### 🎨 Photo Enhancement & Editing

- **Adjust Brightness, Contrast, Saturation:** Fine-tune the visual quality of your photo
- **Real-Time Image Preview:** View edits live before proceeding to the next step
- **Custom Filename and Export Format:** Define image file names and choose between PNG or JPEG

### 📏 Size, Layout, and Background Options

- **Background Replacement:** Choose from preset solid colors (e.g., white, blue) or upload your own background image
- **Custom Cropping Alignment:** Adjust photo alignment to ensure correct face positioning and spacing
- **Face Centering Support:** Provides visual guidance to align faces according to international standards
- **DPI-Compliant Output:** Generates photos at the appropriate DPI for printing or digital submission

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Node.js 14 or higher
- NPM 6 or higher

## Installation

### Backend Setup

1. Navigate to the backend directory:

   ```
   cd backend
   ```

2. **Important:** Add the ONNX model file to the `backend/models` directory:

   - You will need to obtain the ONNX model file separately
   - Ensure the model is compatible with the application

3. Build the application:

   ```
   mvn clean package
   ```

4. Install dependencies:

   ```
   mvn clean install
   ```

5. Run the backend server:
   ```
   mvn spring-boot:run
   ```
   The server will start at http://localhost:8080

### Frontend Setup

1. Navigate to the frontend directory:

   ```
   cd frontend
   ```

2. Install dependencies:

   ```
   npm install
   ```

3. Build the application:

   ```
   npm run build
   ```

4. Start the development server:
   ```
   npm start
   ```
   The application will be available at http://localhost:3000

## Troubleshooting

If you encounter issues with the ONNX model, try changing the ONNX runtime version in the `pom.xml` file:

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.20.0</version>
</dependency>
```

Change the version to `1.21.0` and rebuild:

```
mvn clean install
mvn spring-boot:run
```
  
## Project Structure
**Backend:** Java Spring Boot application handling image processing with OpenCV and AI-based background removal

```
backend/
├── models/
│   └── u2net.onnx                     # Pre-trained U2Net model for background removal
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── passportphotomaker/
│                       ├── controller/             # REST API controllers
│                       │   └── PhotoController.java
│                       └── service/                # Business logic services
│                           ├── PhotoService.java
│                           ├── bgchange/           # Background changing functionality
│                           │   └── BackgroundChanger.java
│                           ├── bgremove/           # Background removal functionality
│                           │   └── BackgroundRemover.java
│                           ├── facedetect/         # Face detection functionality
│                           │   └── FaceDetector.java
│                           ├── imagecrop/          # Image cropping functionality
│                           │   ├── ImageCropper.java
│                           │   └── PassportPhotoCropper.java
│                           └── imageedit/          # Image adjustment functionality
│                               └── ImageAdjuster.java
├── resources/                         # Application resources
└── target/                            # Build output directory
├── beach.png                      # Sample background image
└── debug_images/                  # Debug output from image processing
```

**Frontend:** React + Tailwind CSS application with a step-by-step UI process for photo editing
```
frontend/
├── node_modules/             # NPM dependencies
├── public/                   # Static files
└── src/
    ├── components/           # React components
    │   └── Steps.js          # Step-by-step UI components
    ├── utils/                # Utility functions
    ├── App.js                # Main application component
    ├── constants.js          # Application constants
    ├── index.css             # Global CSS
    ├── index.js              # Application entry point
    ├── postcss.config.js     # PostCSS configuration
    └── tailwind.config.js    # Tailwind CSS configuration
```

## License

This project is licensed under the IS442G1T6 - JK its not a real one.

## Acknowledgments

- OpenCV for image processing
- ONNX Runtime for background segmentation model execution
- React and Tailwind CSS for the frontend interface
