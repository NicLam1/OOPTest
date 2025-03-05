import React, { useState } from 'react';
import './App.css';

function App() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [processedImage, setProcessedImage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleFileChange = (event) => {
    setSelectedFile(event.target.files[0]);
    setProcessedImage(null);
    setError(null);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    
    if (!selectedFile) {
      setError("Please select an image first.");
      return;
    }
    
    const formData = new FormData();
    formData.append('image', selectedFile);
    
    try {
      setLoading(true);
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
          <button type="submit" disabled={!selectedFile || loading}>
            {loading ? 'Processing...' : 'Process Photo'}
          </button>
        </form>

        {error && <div className="error">{error}</div>}

        <div className="image-preview">
          <div className="image-container">
            <h2>Original Image</h2>
            {selectedFile && (
              <img 
                src={URL.createObjectURL(selectedFile)} 
                alt="Original upload" 
              />
            )}
          </div>

          <div className="image-container">
            <h2>Processed Image</h2>
            {processedImage && (
              <img 
                src={processedImage} 
                alt="Processed result" 
              />
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

export default App; 