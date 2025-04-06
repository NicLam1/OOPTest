import React, { useState, useRef } from 'react';
import ReactCrop from 'react-image-crop';
import 'react-image-crop/dist/ReactCrop.css';
import './App.css';

const cropSizes = {
  '2x2': { 
    label: '2x2 inches (US and India)', 
    width: 2,
    height: 2,
    unit: 'inch' 
  }, 
  '35x45': { 
    label: '35x45 mm (UK, Europe, Australia, Singapore, Nigeria)', 
    width: 35,
    height: 45,
    unit: 'mm' 
  },
  '5x7': { 
    label: '5x7 cm (Canada)', 
    width: 5,
    height: 7,
    unit: 'cm' 
  },
  '33x48': { 
    label: '33x48 mm (China)', 
    width: 33,
    height: 48,
    unit: 'mm' 
  },
};

// Standard DPI for passport photos
const DPI = 300;

function App() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [processedImage, setProcessedImage] = useState(null);
  const [backgroundRemovedImage, setBackgroundRemovedImage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedSize, setSelectedSize] = useState('35x45'); // default
  const [downloadFormat, setDownloadFormat] = useState('png');
  const [step, setStep] = useState(1); // 1: Upload & Remove BG, 2: Crop & Process
  
  // Cropping state
  const [crop, setCrop] = useState({
    unit: '%',
    width: 90,
    height: 90,
    x: 5,
    y: 5
  });
  const [completedCrop, setCompletedCrop] = useState(null);
  const imgRef = useRef(null);

  const calculateInitialCrop = (imageWidth, imageHeight, aspectRatio) => {
    // Calculate crop dimensions that maintain aspect ratio
    let cropWidth, cropHeight;
    
    if (imageWidth / imageHeight > aspectRatio) {
      // Image is wider than target ratio - constrain by height
      cropHeight = imageHeight * 0.8; // Use 80% of image height for better face framing
      cropWidth = cropHeight * aspectRatio;
    } else {
      // Image is taller than target ratio - constrain by width
      cropWidth = imageWidth * 0.8; // Use 80% of image width
      cropHeight = cropWidth / aspectRatio;
    }
    
    // Center the crop
    const x = (imageWidth - cropWidth) / 2;
    const y = (imageHeight - cropHeight) / 2;
    
    return {
      unit: 'px',
      width: cropWidth,
      height: cropHeight,
      x: x,
      y: y
    };
  };

  const handleFileChange = (event) => {
    const file = event.target.files[0];
    setSelectedFile(file);
    setImageUrl(URL.createObjectURL(file));
    setProcessedImage(null);
    setBackgroundRemovedImage(null);
    setError(null);
    setStep(1);
    
    // Reset crop when new image is selected - will be properly set when image loads
    setCrop(null);
    setCompletedCrop(null);
  };

  const handleImageLoad = (e) => {
    const { naturalWidth, naturalHeight } = e.target;
    const aspectRatio = cropSizes[selectedSize].width / cropSizes[selectedSize].height;
    const initialCrop = calculateInitialCrop(naturalWidth, naturalHeight, aspectRatio);
    setCrop(initialCrop);
  };

  const handleRemoveBackground = async () => {
    if (!selectedFile) {
      setError("Please select an image first.");
      return;
    }

    try {
      setLoading(true);
      const formData = new FormData();
      formData.append('file', selectedFile);

      const response = await fetch('http://localhost:8080/api/remove-background', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }

      const resultBlob = await response.blob();
      const bgRemovedUrl = URL.createObjectURL(resultBlob);
      setBackgroundRemovedImage(bgRemovedUrl);
      setStep(2);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = (url, format) => {
    const link = document.createElement('a');
    link.href = url;
    link.download = `passport-photo.${format}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };  

  const calculatePixelDimensions = (width, height, unit, dpi = 300) => {
    let unitToInch;
  
    switch (unit.toLowerCase()) {
      case 'mm': unitToInch = 25.4; break;
      case 'cm': unitToInch = 2.54; break;
      case 'inch': unitToInch = 1.0; break;
      default: unitToInch = 25.4; break;
    }
  
    const pixelWidth = Math.round((width / unitToInch) * dpi);
    const pixelHeight = Math.round((height / unitToInch) * dpi);
  
    return { width: pixelWidth, height: pixelHeight };
  };
  

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!backgroundRemovedImage || !completedCrop) {
      setError("Please complete background removal and cropping first.");
      return;
    }

    try {
      setLoading(true);
      
      // Get the cropped image data
      const canvas = document.createElement('canvas');
      const image = imgRef.current;
      
      // Calculate the target dimensions based on the selected format
      const { width: targetWidth, height: targetHeight } = calculatePixelDimensions(
        cropSizes[selectedSize].width,
        cropSizes[selectedSize].height,
        cropSizes[selectedSize].unit
      );

      // Set canvas dimensions to match the target size exactly
      canvas.width = targetWidth;
      canvas.height = targetHeight;
      const ctx = canvas.getContext('2d');
      
      // Enable image smoothing for better quality
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';

      // Calculate scaling factors based on the actual displayed image size
      const scaleX = image.naturalWidth / image.width;
      const scaleY = image.naturalHeight / image.height;

      // Calculate the actual source dimensions in the original image
      const sourceX = Math.round(completedCrop.x * scaleX);
      const sourceY = Math.round(completedCrop.y * scaleY);
      const sourceWidth = Math.round(completedCrop.width * scaleX);
      const sourceHeight = Math.round(completedCrop.height * scaleY);

      // Draw the cropped image directly to the target size
      ctx.drawImage(
        image,
        sourceX,
        sourceY,
        sourceWidth,
        sourceHeight,
        0,
        0,
        targetWidth,
        targetHeight
      );

      // Convert canvas to blob
      const blob = await new Promise(resolve => canvas.toBlob(resolve, `image/${downloadFormat}`, 1.0));
      
      // Create URL for preview
      const croppedImageUrl = URL.createObjectURL(blob);
      setProcessedImage(croppedImageUrl);
      
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>Passport Photo Maker</h1>
      </header>
      <main>
        <div className="step-indicator">
          <div className={`step ${step === 1 ? 'active' : ''}`}>
            <div className="step-number">Step 1</div>
            <div className="step-title">Upload & Remove Background</div>
          </div>
          <div className={`step ${step === 2 ? 'active' : ''}`}>
            <div className="step-number">Step 2</div>
            <div className="step-title">Crop & Process</div>
          </div>
        </div>

        <div className="file-input">
          <label htmlFor="image-upload">Select an image:</label>
          <input
            type="file"
            id="image-upload"
            accept="image/*"
            onChange={handleFileChange}
          />
        </div>

        {step === 1 && imageUrl && (
          <div className="image-preview">
            <h3>Original Image</h3>
            <div className="image-container">
              <img 
                src={imageUrl} 
                alt="Original" 
                style={{ maxWidth: '100%', maxHeight: '300px', objectFit: 'contain' }} 
              />
            </div>
            <button 
              onClick={handleRemoveBackground}
              disabled={loading}
              className="action-button"
            >
              {loading ? 'Removing Background...' : 'Remove Background'}
            </button>
          </div>
        )}

        {step === 2 && (
          <>
            <div className="dropdown-container">
              <label htmlFor="size-select">Select photo size:</label>
              <select
                id="size-select"
                value={selectedSize}
                onChange={(e) => {
                  setSelectedSize(e.target.value);
                  // Recalculate crop when size changes
                  if (imgRef.current) {
                    const { naturalWidth, naturalHeight } = imgRef.current;
                    const newAspectRatio = cropSizes[e.target.value].width / cropSizes[e.target.value].height;
                    const newCrop = calculateInitialCrop(naturalWidth, naturalHeight, newAspectRatio);
                    setCrop(newCrop);
                  }
                }}
              >
                {Object.entries(cropSizes).map(([key, { label }]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
              </select>
            </div>

            <div className="image-preview">
              <h3>Background Removed - Adjust Crop</h3>
              <ReactCrop
                src={backgroundRemovedImage}
                crop={crop}
                onChange={(c) => setCrop(c)}
                onComplete={(c) => setCompletedCrop(c)}
                aspect={cropSizes[selectedSize].width / cropSizes[selectedSize].height}
                minWidth={200}
                minHeight={200}
                ruleOfThirds={true}
                circularCrop={false}
                keepSelection={true}
              >
                <img 
                  ref={imgRef}
                  src={backgroundRemovedImage} 
                  alt="Background Removed" 
                  style={{ maxWidth: '100%', maxHeight: '500px', objectFit: 'contain' }} 
                  onLoad={handleImageLoad}
                />
              </ReactCrop>
              <p className="crop-instructions">
                Drag to adjust the crop area. The face should be centered and take up about 70-80% of the height. The crop box maintains the correct aspect ratio for your selected photo size.
              </p>
              <p className="dimensions-info">
                Selected format: {cropSizes[selectedSize].width}x{cropSizes[selectedSize].height} {cropSizes[selectedSize].unit} 
                ({calculatePixelDimensions(cropSizes[selectedSize].width, cropSizes[selectedSize].height, cropSizes[selectedSize].unit).width}x
                {calculatePixelDimensions(cropSizes[selectedSize].width, cropSizes[selectedSize].height, cropSizes[selectedSize].unit).height} pixels at {DPI} DPI)
              </p>
              <button 
                onClick={handleSubmit}
                disabled={loading || !completedCrop}
                className="action-button"
              >
                {loading ? 'Processing...' : 'Process Photo'}
              </button>
            </div>
          </>
        )}

        {error && <div className="error">{error}</div>}

        {processedImage && (
          <div className="result-preview">
            <h3>Final Result</h3>
            <div className="image-container">
              <img 
                src={processedImage} 
                alt="Processed" 
                style={{ maxWidth: '100%', maxHeight: '300px', objectFit: 'contain' }} 
              />
            </div>
            <button 
              onClick={() => handleDownload(processedImage, downloadFormat)}
              className="download-button"
            >
              Download Photo
            </button>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;