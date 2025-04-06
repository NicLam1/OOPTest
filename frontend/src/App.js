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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedSize, setSelectedSize] = useState('35x45'); // default
  const [downloadFormat, setDownloadFormat] = useState('png');
  
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

  const handleFileChange = (event) => {
    const file = event.target.files[0];
    setSelectedFile(file);
    setImageUrl(URL.createObjectURL(file));
    setProcessedImage(null);
    setError(null);
    
    // Reset crop when new image is selected
    setCrop({
      unit: '%',
      width: 90,
      height: 90,
      x: 5,
      y: 5
    });
    setCompletedCrop(null);
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

    if (!selectedFile) {
      setError("Please select an image first.");
      return;
    }

    try {
      setLoading(true);
      
      // Get the cropped image data
      const canvas = document.createElement('canvas');
      const image = imgRef.current;
      const scaleX = image.naturalWidth / image.width;
      const scaleY = image.naturalHeight / image.height;
      const ctx = canvas.getContext('2d');
      
      // Set canvas dimensions to the cropped area
      canvas.width = completedCrop.width;
      canvas.height = completedCrop.height;
      
      // Draw the cropped image
      ctx.drawImage(
        image,
        completedCrop.x * scaleX,
        completedCrop.y * scaleY,
        completedCrop.width * scaleX,
        completedCrop.height * scaleY,
        0,
        0,
        completedCrop.width,
        completedCrop.height
      );
      
      // Calculate the target dimensions based on the selected format
      const { width: targetWidth, height: targetHeight } = calculatePixelDimensions(
        cropSizes[selectedSize].width,
        cropSizes[selectedSize].height,
        cropSizes[selectedSize].unit
      );
      
      // Create a new canvas for the resized image
      const resizedCanvas = document.createElement('canvas');
      resizedCanvas.width = targetWidth;
      resizedCanvas.height = targetHeight;
      const resizedCtx = resizedCanvas.getContext('2d');
      
      // Draw the cropped image onto the resized canvas
      resizedCtx.drawImage(canvas, 0, 0, targetWidth, targetHeight);
      
      // Convert resized canvas to blob
      resizedCanvas.toBlob(async (blob) => {
        const formData = new FormData();
        formData.append('image', blob, selectedFile.name);
        formData.append('format', downloadFormat);
        
        // Add photo size format information
        formData.append('photoFormat', selectedSize);
        formData.append('photoWidth', cropSizes[selectedSize].width);
        formData.append('photoHeight', cropSizes[selectedSize].height);
        formData.append('photoUnit', cropSizes[selectedSize].unit);

        const response = await fetch('http://localhost:8080/api/process-photo', {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          throw new Error(`Server responded with ${response.status}`);
        }

        const resultBlob = await response.blob();
        setProcessedImage(URL.createObjectURL(resultBlob));
      }, `image/${downloadFormat}`);
      
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
        <form onSubmit={handleSubmit}>
          <div className="file-input">
            <label htmlFor="image-upload">Select an image:</label>
            <input
              type="file"
              id="image-upload"
              accept="image/*"
              onChange={handleFileChange}
            />
          </div>

          <div className="dropdown-container">
            <label htmlFor="size-select">Select photo size:</label>
            <select
              id="size-select"
              value={selectedSize}
              onChange={(e) => setSelectedSize(e.target.value)}
            >
              {Object.entries(cropSizes).map(([key, { label }]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>

          {imageUrl && (
            <div className="image-preview">
              <h3>Original Image</h3>
              <ReactCrop
                src={imageUrl}
                crop={crop}
                onChange={(c) => setCrop(c)}
                onComplete={(c) => setCompletedCrop(c)}
                aspect={cropSizes[selectedSize].width / cropSizes[selectedSize].height}
              >
                <img 
                  ref={imgRef}
                  src={imageUrl} 
                  alt="Original" 
                  style={{ maxWidth: '100%', maxHeight: '300px' }} 
                />
              </ReactCrop>
              <p className="crop-instructions">
                Drag to adjust the crop area. The face should be centered and take up about 70-80% of the height.
              </p>
              <p className="dimensions-info">
                Selected format: {cropSizes[selectedSize].width}x{cropSizes[selectedSize].height} {cropSizes[selectedSize].unit} 
                ({calculatePixelDimensions(cropSizes[selectedSize].width, cropSizes[selectedSize].height, cropSizes[selectedSize].unit).width}x
                {calculatePixelDimensions(cropSizes[selectedSize].width, cropSizes[selectedSize].height, cropSizes[selectedSize].unit).height} pixels at {DPI} DPI)
              </p>
            </div>
          )}

          <button 
            type="submit" 
            disabled={!selectedFile || loading || !completedCrop}
          >
            {loading ? 'Processing...' : 'Process Photo'}
          </button>
        </form>

        {error && <div className="error">{error}</div>}

        <div className="image-preview">
          <div className="image-container">
            <h2>Processed Image</h2>
            {processedImage && (
              <>
                <img src={processedImage} alt="Processed result" style={{ maxWidth: '100%', marginBottom: '1rem' }} />
          
                <div className="download-section" style={{ marginTop: '1rem' }}>
                  <label htmlFor="format-select">Choose format to download:</label>
                  <select
                    id="format-select"
                    value={downloadFormat}
                    onChange={(e) => setDownloadFormat(e.target.value)}
                    style={{ margin: '0 1rem' }}
                  >
                    <option value="png">PNG</option>
                    <option value="jpeg">JPEG</option>
                  </select>

                  <button
                    type="button"
                    onClick={() => handleDownload(processedImage, downloadFormat)}
                  >
                    Download Image
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;