import React, { useState, useCallback } from 'react';
import Cropper from 'react-easy-crop';
import './App.css';
import getCroppedImg from './cropImage';

const cropSizes = {
  '2x2': { label: '2x2 inches (US and India)', aspect: 1 }, 
  '35x45': { label: '35x45 mm (UK, Europe, Australia, Singapore, Nigeria)', aspect: 35 / 45 },
  '5x7': { label: '5x7 cm (Canada)', aspect: 5 / 7 },
  '33x48': { label: '33x48 mm (China)', aspect: 33 / 48 },
};

function App() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [processedImage, setProcessedImage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null);
  const [selectedSize, setSelectedSize] = useState('35x45'); // default
  const [downloadFormat, setDownloadFormat] = useState('png');


  const handleFileChange = (event) => {
    const file = event.target.files[0];
    setSelectedFile(file);
    setImageUrl(URL.createObjectURL(file));
    setProcessedImage(null);
    setError(null);
    setZoom(1); // reset zoom
  };

  const handleDownload = (url, format) => {
    const link = document.createElement('a');
    link.href = url;
    link.download = `passport-photo.${format}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };  

  const onCropComplete = useCallback((croppedArea, croppedAreaPixels) => {
    setCroppedAreaPixels(croppedAreaPixels);
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!imageUrl || !croppedAreaPixels) {
      setError("Please select and crop an image first.");
      return;
    }

    try {
      setLoading(true);
      const croppedBlob = await getCroppedImg(imageUrl, croppedAreaPixels);

      const formData = new FormData();
      formData.append('image', croppedBlob, 'cropped.jpg');
      formData.append('format', downloadFormat);

      const response = await fetch('http://localhost:8080/api/process-photo', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }

      const blob = await response.blob();
      setProcessedImage(URL.createObjectURL(blob));
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
            <>
              <div className="crop-container">
                <Cropper
                  image={imageUrl}
                  crop={crop}
                  zoom={zoom}
                  aspect={cropSizes[selectedSize].aspect}
                  onCropChange={setCrop}
                  onZoomChange={setZoom}
                  onCropComplete={onCropComplete}
                />
              </div>

              <div className="slider-container">
                <label htmlFor="zoom">Zoom:</label>
                <input
                  id="zoom"
                  type="range"
                  min={1}
                  max={3}
                  step={0.01}
                  value={zoom}
                  onChange={(e) => setZoom(Number(e.target.value))}
                />
              </div>
            </>
          )}

          <button type="submit" disabled={!selectedFile || loading}>
            {loading ? 'Processing...' : 'Process Photo'}
          </button>
        </form>

        {error && <div className="error">{error}</div>}

        <div className="image-preview">
        <div className="image-container">
          <h2>Processed Image</h2>
            {processedImage && (
              <>
              <img src={processedImage} alt="Processed result in ${downloadFormat}" style={{ maxWidth: '100%', marginBottom: '1rem' }} />
        
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
