import React, { useState, useRef } from 'react';
import ReactCrop from 'react-image-crop';
import 'react-image-crop/dist/ReactCrop.css'; 
import { 
  ArrowUpTrayIcon, 
  PhotoIcon,
  CheckCircleIcon,
  ArrowPathIcon,
  ScissorsIcon,
  DocumentArrowDownIcon,
  PaintBrushIcon
} from '@heroicons/react/24/outline';
import { debounce } from 'lodash'; 

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
  const [step, setStep] = useState(1); // 1: Upload & Remove BG, 2: Change BG, 3: Adjust Photo, 4: Crop & Process
  const [backgroundChangedImage, setBackgroundChangedImage] = useState(null);
  const [backgroundImage, setBackgroundImage] = useState(null);
  const [backgroundType, setBackgroundType] = useState('color'); // 'color', 'image' or 'picker'
  const [selectedBackgroundColor, setSelectedBackgroundColor] = useState('#ffffff'); // default white
  const [brightness, setBrightness] = useState(0);   // -100 to 100
  const [contrast, setContrast] = useState(1);       // 0.5 to 3
  const [saturation, setSaturation] = useState(1);   // 0.5 to 3
  const [backgroundRemovedFile, setBackgroundRemovedFile] = useState(null);
  const [finalAdjustedImage, setFinalAdjustedImage] = useState(null);
  const [customFilename, setCustomFilename] = useState('passport-photo');
  const [backgroundScale, setBackgroundScale] = useState(1.0);
  const [backgroundOffsetX, setBackgroundOffsetX] = useState(0.0);
  const [backgroundOffsetY, setBackgroundOffsetY] = useState(0.0);
  const [imageSize, setImageSize] = useState({ width: 0, height: 0 });



  // Update debouncedSendAdjustments to set finalAdjustedImage after applying adjustments
  const debouncedSendAdjustments = debounce(
    async ({ brightness, contrast, saturation }) => {
      if (!backgroundRemovedFile) return;
  
      console.log("Sending to /adjust-photo", { brightness, contrast, saturation });
  
      const formData = new FormData();
      formData.append("image", backgroundRemovedFile);
      formData.append("brightness", brightness);
      formData.append("contrast", contrast);
      formData.append("saturation", saturation);
      formData.append("format", downloadFormat);
  
      try {
        const response = await fetch("http://localhost:8080/api/adjust-photo", {
          method: "POST",
          body: formData,
        });
  
        if (!response.ok) {
          console.error("Adjustment request failed:", response.status);
          return;
        }
  
        const adjustedBlob = await response.blob();
        console.log("Received adjusted image blob:", adjustedBlob);
        
      // Create new File from the adjusted blob
      const adjustedFile = new File([adjustedBlob], "adjusted.png", { type: adjustedBlob.type });
      
      // Update the file reference for future adjustments
      setBackgroundRemovedFile(adjustedFile);
      
      // Update UI with the adjusted image
      const adjustedImageUrl = URL.createObjectURL(adjustedBlob);
      setBackgroundChangedImage(adjustedImageUrl);
      setFinalAdjustedImage(adjustedImageUrl);
    } catch (error) {
      console.error("Error during adjustment:", error);
    }
  },
  300
  );
  
  
  const [isPickingColor, setIsPickingColor] = useState(false);
  
  // Background colors
  const backgroundColors = [
    { name: 'White', value: '#ffffff' },
    { name: 'Blue', value: '#0284c7' },
    { name: 'Red', value: '#dc2626' },
    { name: 'Gray', value: '#9ca3af' },
    { name: 'Black', value: '#000000' },
  ];

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

  // Function to ensure a crop stays within image boundaries
  const ensureCropWithinBounds = (crop, imageWidth, imageHeight) => {
    let x = crop.x;
    let y = crop.y;
    let width = crop.width;
    let height = crop.height;
    
    // If crop extends beyond right edge
    if (x + width > imageWidth) {
      // Adjust x position first
      x = Math.max(0, imageWidth - width);
      
      // If it's still not fitting, reduce width
      if (x + width > imageWidth) {
        width = imageWidth - x;
      }
    }
    
    // If crop extends beyond bottom edge
    if (y + height > imageHeight) {
      // Adjust y position first
      y = Math.max(0, imageHeight - height);
      
      // If it's still not fitting, reduce height
      if (y + height > imageHeight) {
        height = imageHeight - y;
      }
    }
    
    return {
      ...crop,
      x,
      y,
      width,
      height
    };
  };

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
    const x = Math.max(0, (imageWidth - cropWidth) / 2);
    const y = Math.max(0, (imageHeight - cropHeight) / 2);
    
    return ensureCropWithinBounds({
      unit: 'px',
      width: cropWidth,
      height: cropHeight,
      x: x,
      y: y
    }, imageWidth, imageHeight);
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
    
    // Store image dimensions for future reference
    setImageSize({ width: naturalWidth, height: naturalHeight });
    
    const aspectRatio = cropSizes[selectedSize].width / cropSizes[selectedSize].height;
    const initialCrop = calculateInitialCrop(naturalWidth, naturalHeight, aspectRatio);
    
    // Log initial crop for debugging
    console.log('Initial crop:', initialCrop);
    console.log('Image dimensions:', naturalWidth, 'x', naturalHeight);
    
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
      formData.append('image', selectedFile);  // Match the @RequestParam("image") in controller
      formData.append('format', downloadFormat || 'png');  // Add format

      const response = await fetch('http://localhost:8080/api/process-photo', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }

      const resultBlob = await response.blob();
      const file = new File([resultBlob], "transparent.png", { type: resultBlob.type });

      setBackgroundRemovedImage(URL.createObjectURL(file)); // For preview
      setBackgroundRemovedFile(file); // NEW STATE

      setStep(2);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };
  


  const handleDownload = (url, format) => {
    console.log("Downloading with filename:", customFilename); 
    const filename = customFilename.trim() || 'passport-photo';
    const link = document.createElement('a');
    link.href = url;
    link.download = `passport-photo.${format}`;
    link.download = `${filename}.${format}`;
    console.log("Download attribute set to:", link.download);
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

    if (!backgroundChangedImage || !completedCrop) {
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

  const handleBackgroundImageChange = (event) => {
    const file = event.target.files[0];
    if (file) {
      setBackgroundImage(file);
      setBackgroundType('image');
    }
  };
  
  const handleChangeBackground = async () => {
    if (!backgroundRemovedImage) {
      setError("Please complete background removal first.");
      return;
    }
  
    try {
      setLoading(true);
      
      // Get the image as a blob
      const response = await fetch(backgroundRemovedImage);
      const imageBlob = await response.blob();
      
      const formData = new FormData();
      formData.append('image', imageBlob, 'transparent-image.png');
      formData.append('format', downloadFormat || 'png');
      
      // Add either background color or image based on selected type
      if (backgroundType === 'color') {
        formData.append('backgroundColor', selectedBackgroundColor);
      } else if (backgroundType === 'image' && backgroundImage) {
        formData.append('backgroundImg', backgroundImage);
        // Add background resizing parameters
        formData.append('bgScale', backgroundScale);
        formData.append('bgOffsetX', backgroundOffsetX);
        formData.append('bgOffsetY', backgroundOffsetY);
      }
  
      const apiResponse = await fetch('http://localhost:8080/api/process-photo', {
        method: 'POST',
        body: formData,
      });
  
      if (!apiResponse.ok) {
        throw new Error(`Server responded with ${apiResponse.status}`);
      }
  
      const resultBlob = await apiResponse.blob();
      const bgChangedUrl = URL.createObjectURL(resultBlob);
  
      setBackgroundChangedImage(bgChangedUrl);

      // IMPORTANT: Set finalAdjustedImage to the background changed image as the starting point (fix the bug of uploaded background image not being used)
      setFinalAdjustedImage(bgChangedUrl);
  
      // Create a file from the blob for adjustment API calls
      const bgChangedFile = new File([resultBlob], "bg-changed.png", { type: resultBlob.type });
      setBackgroundRemovedFile(bgChangedFile); // Update to use the background-changed file

      // Reset brightness, contrast, and saturation to default values
      setBrightness(0);
      setContrast(1);
      setSaturation(1);
      
      // Now move to step 3 (adjustments)
      setStep(3);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAdjustment = (key, value) => {
    console.log("Adjustment triggered:", key, value);
  
    // Update state
    if (key === 'brightness') setBrightness(value);
    if (key === 'contrast') setContrast(value);
    if (key === 'saturation') setSaturation(value);
  
    // Call the debounced function with the updated values
    debouncedSendAdjustments({
      brightness: key === 'brightness' ? value : brightness,
      contrast: key === 'contrast' ? value : contrast,
      saturation: key === 'saturation' ? value : saturation,
    });
  };
  
  // Function to handle color picking from the image
  const handleImageColorPick = (e) => {
    if (!isPickingColor) return;
    
    // Get canvas context from the image
    const canvas = document.createElement('canvas');
    const img = e.target;
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0);
    
    // Get the pixel data at the click position
    const rect = img.getBoundingClientRect();
    const x = Math.round((e.clientX - rect.left) * (img.naturalWidth / rect.width));
    const y = Math.round((e.clientY - rect.top) * (img.naturalHeight / rect.height));
    const pixelData = ctx.getImageData(x, y, 1, 1).data;
    
    // Convert to hex
    const hex = '#' + 
      ('0' + pixelData[0].toString(16)).slice(-2) +
      ('0' + pixelData[1].toString(16)).slice(-2) +
      ('0' + pixelData[2].toString(16)).slice(-2);
    
    // Update color and exit picking mode
    setSelectedBackgroundColor(hex);
    setIsPickingColor(false);
    setBackgroundType('color'); // Switch back to color mode with the picked color
  };

  // Handler for crop changes to ensure it stays within bounds
  const handleCropChange = (newCrop) => {
    if (imgRef.current) {
      const { naturalWidth, naturalHeight } = imgRef.current;
      const validatedCrop = ensureCropWithinBounds(newCrop, naturalWidth, naturalHeight);
      setCrop(validatedCrop);
    } else {
      setCrop(newCrop);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold text-gray-900 flex items-center">
            <PhotoIcon className="h-8 w-8 mr-2 text-primary-600" />
            Passport Photo Maker
          </h1>
        </div>
      </header>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        {/* Progress Steps */}
        <div className="px-4 sm:px-0 mb-8">
          <div className="border-b border-gray-200">
            <nav className="-mb-px flex" aria-label="Tabs">
              <button
                className={`w-1/4 py-4 px-1 text-center border-b-2 font-medium text-sm ${
                  step === 1
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => step > 1 && setStep(1)}
              >
                <div className="flex justify-center items-center">
                  <span className="bg-gray-100 rounded-full h-6 w-6 flex items-center justify-center mr-2 border border-gray-300">
                    1
                  </span>
                  <span className="hidden sm:inline">Upload & Remove Background</span>
                  <span className="sm:hidden">Upload</span>
                </div>
              </button>
              <button
                className={`w-1/4 py-4 px-1 text-center border-b-2 font-medium text-sm ${
                  step === 2
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => backgroundRemovedImage && setStep(2)}
                disabled={!backgroundRemovedImage}
              >
                <div className="flex justify-center items-center">
                  <span className="bg-gray-100 rounded-full h-6 w-6 flex items-center justify-center mr-2 border border-gray-300">
                    2
                  </span>
                  <span className="hidden sm:inline">Change Background</span>
                  <span className="sm:hidden">Background</span>
                </div>
              </button>
              <button
                className={`w-1/4 py-4 px-1 text-center border-b-2 font-medium text-sm ${
                  step === 3
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => backgroundChangedImage && setStep(3)}
                disabled={!backgroundChangedImage}
              >
                <div className="flex justify-center items-center">
                  <span className="bg-gray-100 rounded-full h-6 w-6 flex items-center justify-center mr-2 border border-gray-300">
                    3
                  </span>
                  <span className="hidden sm:inline">Adjust Photo</span>
                  <span className="sm:hidden">Adjust</span>
                </div>
              </button>
              <button
                className={`w-1/4 py-4 px-1 text-center border-b-2 font-medium text-sm ${
                  step === 4
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                onClick={() => finalAdjustedImage && setStep(4)}
                disabled={!finalAdjustedImage}
              >
                <div className="flex justify-center items-center">
                  <span className="bg-gray-100 rounded-full h-6 w-6 flex items-center justify-center mr-2 border border-gray-300">
                    4
                  </span>
                  <span className="hidden sm:inline">Crop & Process</span>
                  <span className="sm:hidden">Crop</span>
                </div>
              </button>
            </nav>
          </div>
        </div>

        {/* Error Display */}
        {error && (
          <div className="rounded-md bg-red-50 p-4 mx-4 mb-6">
            <div className="flex">
              <div className="flex-shrink-0">
                <svg className="h-5 w-5 text-red-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <p className="text-sm font-medium text-red-800">{error}</p>
              </div>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:p-6">
              {/* Photo Size Selection */}
              <div className="mb-6">
                <label htmlFor="size-select" className="block text-sm font-medium text-gray-700 mb-2">
                  Passport Photo Size
                </label>
                <select
                  id="size-select"
                  value={selectedSize}
                  onChange={(e) => setSelectedSize(e.target.value)}
                  className="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm rounded-md"
                >
                  {Object.entries(cropSizes).map(([key, size]) => (
                    <option key={key} value={key}>
                      {size.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* File Upload Section */}
              <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 mt-4 text-center">
                <div className="space-y-1 text-center">
                  <svg
                    className="mx-auto h-12 w-12 text-gray-400"
                    stroke="currentColor"
                    fill="none"
                    viewBox="0 0 48 48"
                    aria-hidden="true"
                  >
                    <path
                      d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                      strokeWidth={2}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                  <div className="flex text-sm text-gray-600 justify-center">
                    <label
                      htmlFor="file-upload"
                      className="relative cursor-pointer bg-white rounded-md font-medium text-primary-600 hover:text-primary-500 focus-within:outline-none focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-primary-500"
                    >
                      <span>Upload a file</span>
                      <input
                        id="file-upload"
                        name="file-upload"
                        type="file"
                        className="sr-only"
                        accept="image/*"
                        onChange={handleFileChange}
                      />
                    </label>
                    <p className="pl-1">or drag and drop</p>
                  </div>
                  <p className="text-xs text-gray-500">PNG, JPG, GIF up to 10MB</p>
                </div>

                {/* Image Preview */}
                {imageUrl && (
                  <div className="mt-6">
                    <img
                      src={imageUrl}
                      alt="Uploaded"
                      className="mx-auto max-h-64 rounded-lg shadow-md"
                    />
                  </div>
                )}
              </div>

              {/* Output Format Selection */}
              <div className="mt-6">
                <label htmlFor="format-select" className="block text-sm font-medium text-gray-700 mb-2">
                  Output Format
                </label>
                <select
                  id="format-select"
                  value={downloadFormat}
                  onChange={(e) => setDownloadFormat(e.target.value)}
                  className="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-primary-500 focus:border-primary-500 sm:text-sm rounded-md"
                >
                  <option value="png">PNG</option>
                  <option value="jpeg">JPEG</option>
                </select>
              </div>

              {/* Remove Background Button */}
              <div className="mt-6">
                <button
                  onClick={handleRemoveBackground}
                  disabled={!selectedFile || loading}
                  className={`inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white ${
                    !selectedFile || loading
                      ? 'bg-gray-300 cursor-not-allowed'
                      : 'bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500'
                  }`}
                >
                  {loading ? (
                    <>
                      <ArrowPathIcon className="animate-spin -ml-1 mr-2 h-5 w-5" />
                      Processing...
                    </>
                  ) : (
                    <>
                      <CheckCircleIcon className="-ml-1 mr-2 h-5 w-5" />
                      Remove Background
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:p-6">
              <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4">Change Background</h3>
              <p className="text-sm text-gray-500 mb-6">
                Choose a background color or upload an image to use as the background.
              </p>

              {/* Background Preview */}
              <div className="mb-6">
                <h4 className="text-sm font-medium text-gray-700 mb-2">Preview</h4>
                <div className="bg-gray-100 border border-gray-200 rounded-lg p-4 flex justify-center items-center overflow-hidden">
                  {backgroundRemovedImage ? (
                    <div className="relative" style={{ maxWidth: '100%', maxHeight: '300px', overflow: 'hidden' }}>
                      {backgroundType === 'image' && backgroundImage && (
                        <div 
                          style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            backgroundImage: `url(${URL.createObjectURL(backgroundImage)})`,
                            backgroundSize: `${backgroundScale * 100}%`,
                            backgroundPosition: `${50 + (backgroundOffsetX * 50)}% ${50 + (backgroundOffsetY * 50)}%`,
                            zIndex: 0
                          }}
                        />
                      )}
                      
                      {backgroundType === 'color' && (
                        <div 
                          className="absolute inset-0"
                          style={{ 
                            backgroundColor: selectedBackgroundColor,
                            zIndex: 0
                          }}
                        />
                      )}
                      
                      <img
                        src={backgroundChangedImage || backgroundRemovedImage}
                        alt="Preview"
                        className="relative z-10 max-h-64"
                      />
                    </div>
                  ) : (
                    <div className="text-gray-400 py-8">No image preview available</div>
                  )}
                </div>
              </div>

              {/* Background Type Selection */}
              <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 mb-2">Background Type</label>
                <div className="flex space-x-4 mb-4">
                  <div className="flex items-center">
                    <input
                      id="color-type"
                      name="background-type"
                      type="radio"
                      checked={backgroundType === 'color'}
                      onChange={() => {
                        setBackgroundType('color');
                        setIsPickingColor(false);
                      }}
                      className="focus:ring-primary-500 h-4 w-4 text-primary-600 border-gray-300"
                    />
                    <label htmlFor="color-type" className="ml-2 block text-sm font-medium text-gray-700">
                      Solid Color
                    </label>
                  </div>
                  <div className="flex items-center">
                    <input
                      id="image-type"
                      name="background-type"
                      type="radio"
                      checked={backgroundType === 'image'}
                      onChange={() => {
                        setBackgroundType('image');
                        setIsPickingColor(false);
                      }}
                      className="focus:ring-primary-500 h-4 w-4 text-primary-600 border-gray-300"
                    />
                    <label htmlFor="image-type" className="ml-2 block text-sm font-medium text-gray-700">
                      Image
                    </label>
                  </div>
                </div>
              </div>

              {/* Color controls - only visible when 'color' type is selected */}
              {backgroundType === 'color' && (
                <div className="mb-6">
                  <label className="block text-sm font-medium text-gray-700 mb-2">Select Color</label>
                  
                  {/* Color Presets */}
                  <div className="flex flex-wrap gap-2 mb-3">
                    {backgroundColors.map(color => (
                      <button
                        key={color.value}
                        type="button"
                        onClick={() => setSelectedBackgroundColor(color.value)}
                        className={`h-8 w-8 rounded-full focus:outline-none ${
                          selectedBackgroundColor === color.value ? 'ring-2 ring-offset-2 ring-primary-500' : ''
                        }`}
                        style={{ backgroundColor: color.value }}
                        title={color.name}
                      ></button>
                    ))}
                  </div>
                  
                  {/* Custom Color Picker */}
                  <label className="block text-sm font-medium text-gray-700 mb-2">Cutom Color </label>
                  <div className="flex items-center mt-3"> 
                    <input
                      type="color"
                      value={selectedBackgroundColor}
                      onChange={(e) => setSelectedBackgroundColor(e.target.value)}
                      className="h-8 w-8 p-0 border-0"
                    />
                    <span className="ml-2 text-sm text-gray-500">{selectedBackgroundColor}</span>
                  </div>
                  
                  {/* Pick color from image section */}
                  <div className="mt-4 border-t border-gray-200 pt-4">
                    <button
                      type="button"
                      onClick={() => setIsPickingColor(!isPickingColor)}
                      className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md text-primary-600 bg-primary-50 hover:bg-primary-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
                    >
                      {isPickingColor ? 'Cancel picking' : 'Pick color from image'}
                    </button>
                    
                    {isPickingColor && imageUrl && (
                      <div className="mt-3">
                        <p className="text-sm text-gray-500 mb-2">Click on the image to select a color</p>
                        <div className="border border-gray-300 rounded-md overflow-hidden cursor-crosshair">
                          <img
                            src={imageUrl}
                            alt="Original"
                            className="max-w-full h-auto max-h-48"
                            onClick={handleImageColorPick}
                          />
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Image background controls - only visible when 'image' type is selected */}
              {backgroundType === 'image' && (
                <>
                  {/* Background Resize Controls - only if an image is uploaded */}
                  {backgroundImage && (
                    <div className="mb-6">
                      <h4 className="text-sm font-medium text-gray-700 mb-2">Background Resize Controls</h4>
                      
                      {/* Scale Control */}
                      <div className="mb-3">
                        <label className="block text-sm text-gray-600 mb-1">
                          Scale ({backgroundScale.toFixed(1)})
                        </label>
                        <input
                          type="range"
                          min="0.5"
                          max="2.0"
                          step="0.1"
                          value={backgroundScale}
                          onChange={(e) => setBackgroundScale(parseFloat(e.target.value))}
                          className="w-full"
                        />
                        <p className="text-xs text-gray-500 mt-1">
                          Adjust how zoomed in/out the background appears
                        </p>
                      </div>
                      
                      {/* X Position Control */}
                      <div className="mb-3">
                        <label className="block text-sm text-gray-600 mb-1">
                          Horizontal Position ({backgroundOffsetX.toFixed(1)})
                        </label>
                        <input
                          type="range"
                          min="-1.0"
                          max="1.0"
                          step="0.1"
                          value={backgroundOffsetX}
                          onChange={(e) => setBackgroundOffsetX(parseFloat(e.target.value))}
                          className="w-full"
                        />
                        <p className="text-xs text-gray-500 mt-1">
                          Move background left/right
                        </p>
                      </div>
                      
                      {/* Y Position Control */}
                      <div className="mb-3">
                        <label className="block text-sm text-gray-600 mb-1">
                          Vertical Position ({backgroundOffsetY.toFixed(1)})
                        </label>
                        <input
                          type="range"
                          min="-1.0"
                          max="1.0"
                          step="0.1"
                          value={backgroundOffsetY}
                          onChange={(e) => setBackgroundOffsetY(parseFloat(e.target.value))}
                          className="w-full"
                        />
                        <p className="text-xs text-gray-500 mt-1">
                          Move background up/down
                        </p>
                      </div>
                      
                      {/* Reset Button */}
                      <button
                        type="button"
                        onClick={() => {
                          setBackgroundScale(1.0);
                          setBackgroundOffsetX(0.0);
                          setBackgroundOffsetY(0.0);
                        }}
                        className="mt-2 inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md text-primary-600 bg-primary-50 hover:bg-primary-100"
                      >
                        Reset Position
                      </button>
                    </div>
                  )}

                  {/* Upload Background Image - always shown when 'image' type is selected */}
                  <div className="mb-6">
                    <label className="block text-sm font-medium text-gray-700 mb-2">Upload Background Image</label>
                    <div className="mt-1 flex justify-center px-6 pt-5 pb-6 border-2 border-gray-300 border-dashed rounded-md">
                      <div className="space-y-1 text-center">
                        <svg
                          className="mx-auto h-12 w-12 text-gray-400"
                          stroke="currentColor"
                          fill="none"
                          viewBox="0 0 48 48"
                          aria-hidden="true"
                        >
                          <path
                            d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                            strokeWidth={2}
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                        <div className="flex text-sm text-gray-600 justify-center">
                          <label
                            htmlFor="bg-upload"
                            className="relative cursor-pointer bg-white rounded-md font-medium text-primary-600 hover:text-primary-500 focus-within:outline-none focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-primary-500"
                          >
                            <span>Upload an image</span>
                            <input
                              id="bg-upload"
                              name="bg-upload"
                              type="file"
                              className="sr-only"
                              accept="image/*"
                              onChange={handleBackgroundImageChange}
                            />
                          </label>
                        </div>
                        <p className="text-xs text-gray-500">PNG, JPG, GIF up to 10MB</p>
                      </div>
                    </div>
                    {backgroundImage && (
                      <div className="mt-2 text-sm text-gray-500">
                        Selected: {backgroundImage.name}
                      </div>
                    )}
                  </div>
                </>
              )}
          

                <div className="mb-6">
                  <h4 className="text-sm font-medium text-gray-700 mb-2">Adjust original image</h4>

                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm text-gray-600 mb-1">Brightness ({brightness})</label>
                      <input
                        type="range"
                        min={-100}
                        max={100}
                        value={brightness}
                        onChange={(e) => handleAdjustment('brightness', parseInt(e.target.value))}
                        className="w-full"
                      />
                    </div>

                    <div>
                       <label className="block text-sm text-gray-600 mb-1">Contrast ({contrast.toFixed(2)})</label>
                      <input
                        type="range"
                        min={0.5}
                        max={3}
                        step={0.05}
                        value={contrast}
                        onChange={(e) => handleAdjustment('contrast', parseFloat(e.target.value))}
                        className="w-full"
                      />
                    </div>

                    <div>
                      <label className="block text-sm text-gray-600 mb-1">Saturation ({saturation.toFixed(2)})</label>
                      <input
                       type="range"
                        min={0.5}
                        max={3}
                        step={0.05}
                        value={saturation}
                        onChange={(e) => handleAdjustment('saturation', parseFloat(e.target.value))}
                        className="w-full"
                      />
                     </div>
                    </div>
                  </div>

              <div className="mt-6">
                <button
                  onClick={handleChangeBackground}
                  disabled={!backgroundRemovedImage || loading}
                  className={`inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white ${
                    !backgroundRemovedImage|| loading
                      ? 'bg-gray-300 cursor-not-allowed'
                      : 'bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500'
                  }`}
                >
                  {loading ? (
                    <>
                      <ArrowPathIcon className="animate-spin -ml-1 mr-2 h-5 w-5" />
                      Processing...
                    </>
                  ) : (
                    <>
                      <PaintBrushIcon className="-ml-1 mr-2 h-5 w-5" />
                      Apply Background
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      {step === 3 && (
        <div className="bg-white shadow overflow-hidden sm:rounded-lg">
          <div className="px-4 py-5 sm:p-6">
            <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4">Adjust Photo</h3>
            <p className="text-sm text-gray-500 mb-6">
              Fine-tune your photo with these adjustment controls to get perfect results.
            </p>

            {/* Image Preview */}
            <div className="mb-6">
              <h4 className="text-sm font-medium text-gray-700 mb-2">Preview</h4>
              <div className="bg-gray-100 border border-gray-200 rounded-lg p-4 flex justify-center items-center">
                {backgroundChangedImage ? (
                  <img
                    src={finalAdjustedImage || backgroundChangedImage}
                    alt="Preview"
                    className="max-h-64 rounded shadow-sm"
                  />
                ) : (
                  <div className="text-gray-400 py-8">No image preview available</div>
                )}
              </div>
            </div>

            {/* Adjustment Controls */}
            <div className="mb-6">
              <h4 className="text-sm font-medium text-gray-700 mb-2">Image Adjustments</h4>

              <div className="space-y-4 mt-4">
                <div>
                  <label className="block text-sm text-gray-600 mb-1">Brightness ({brightness})</label>
                  <input
                    type="range"
                    min={-100}
                    max={100}
                    value={brightness}
                    onChange={(e) => handleAdjustment('brightness', parseInt(e.target.value))}
                    className="w-full"
                  />
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>Darker</span>
                    <span>Original</span>
                    <span>Brighter</span>
                  </div>
                </div>

                <div>
                  <label className="block text-sm text-gray-600 mb-1">Contrast ({contrast.toFixed(2)})</label>
                  <input
                    type="range"
                    min={0.5}
                    max={3}
                    step={0.05}
                    value={contrast}
                    onChange={(e) => handleAdjustment('contrast', parseFloat(e.target.value))}
                    className="w-full"
                  />
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>Less</span>
                    <span>Original</span>
                    <span>More</span>
                  </div>
                </div>

                <div>
                  <label className="block text-sm text-gray-600 mb-1">Saturation ({saturation.toFixed(2)})</label>
                  <input
                    type="range"
                    min={0.5}
                    max={3}
                    step={0.05}
                    value={saturation}
                    onChange={(e) => handleAdjustment('saturation', parseFloat(e.target.value))}
                    className="w-full"
                  />
                  <div className="flex justify-between text-xs text-gray-500 mt-1">
                    <span>Less</span>
                    <span>Original</span>
                    <span>More</span>
                  </div>
                </div>
              </div>

              {/* Reset Button */}
              <button
                type="button"
                onClick={() => {
                  setBrightness(0);
                  setContrast(1);
                  setSaturation(1);
                  debouncedSendAdjustments({
                    brightness: 0,
                    contrast: 1,
                    saturation: 1
                  });
                }}
                className="mt-4 inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md text-primary-600 bg-primary-50 hover:bg-primary-100"
              >
                Reset All Adjustments
              </button>
            </div>

            {/* Continue Button */}
            <div className="flex justify-center mt-6">
              <button
                onClick={() => setStep(4)}
                disabled={!finalAdjustedImage || loading}
                className={`inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white ${
                  !finalAdjustedImage || loading
                    ? 'bg-gray-300 cursor-not-allowed'
                    : 'bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500'
                }`}
              >
                {loading ? (
                  <>
                    <ArrowPathIcon className="animate-spin -ml-1 mr-2 h-5 w-5" />
                    Processing...
                  </>
                ) : (
                  <>
                    <ScissorsIcon className="-ml-1 mr-2 h-5 w-5" />
                    Continue to Crop
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
        {step === 4 && (
        <div className="bg-white shadow overflow-hidden sm:rounded-lg">
          <div className="px-4 py-5 sm:p-6">
            <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4">Crop Photo</h3>
            <p className="text-sm text-gray-500 mb-6">
              Adjust the cropping area to ensure proper head position and size for your {cropSizes[selectedSize].label}.
            </p>

            {/* Crop Container */}
            <div className="mb-6 max-w-2xl mx-auto">
              {finalAdjustedImage && (
                <ReactCrop
                  crop={crop}
                  onChange={(c) => setCrop(c)}
                  onComplete={(c) => setCompletedCrop(c)}
                  aspect={cropSizes[selectedSize].width / cropSizes[selectedSize].height}
                  className="rounded-lg max-h-[500px] mx-auto"
                >
                  <img
                    ref={imgRef}
                    src={finalAdjustedImage}
                    alt="Adjusted Image"
                    onLoad={handleImageLoad}
                    className="max-w-full max-h-[500px]"
                  />
                </ReactCrop>
              )}
              
              <div className="mt-2 text-sm text-gray-500">
                <p>
                  Drag to move. Drag corners to resize. The person's eyes should be about 2/3 from the bottom of the photo.
                </p>
              </div>
            </div>

            {/* Process Button */}
            <div className="flex justify-center mt-4">
              <button
                onClick={handleSubmit}
                disabled={!completedCrop || loading}
                className={`inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white ${
                  !completedCrop || loading
                    ? 'bg-gray-300 cursor-not-allowed'
                    : 'bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500'
                }`}
              >
                {loading ? (
                  <>
                    <ArrowPathIcon className="animate-spin -ml-1 mr-2 h-5 w-5" />
                    Processing...
                  </>
                ) : (
                  <>
                    <ScissorsIcon className="-ml-1 mr-2 h-5 w-5" />
                    Crop & Process
                  </>
                )}
              </button>
            </div>

            {/* Result Preview */}
            {processedImage && (
              <div className="mt-8 bg-gray-50 p-6 rounded-lg border border-gray-200 max-w-xl mx-auto">
                <h3 className="text-lg font-medium text-gray-900 mb-4">Processed Photo</h3>
                
                <div className="bg-white p-2 rounded-lg shadow-sm border border-gray-100 mb-4">
                  <img 
                    src={processedImage} 
                    alt="Processed" 
                    className="mx-auto max-h-96 rounded"
                  />
                </div>
                
                <div className="text-sm text-gray-500 mb-4">
                  <p>
                    Final size: {cropSizes[selectedSize].width}×{cropSizes[selectedSize].height} {cropSizes[selectedSize].unit}.
                    {/* {' at '}{DPI} DPI. */}
                  </p>
                </div>

                {/* Filename input field */}
                <div className="mb-4">
                  <label htmlFor="filename" className="block text-sm font-medium text-gray-700 mb-1">
                    Filename
                  </label>
                  <div className="mt-1 flex rounded-md shadow-sm">
                    <input
                      type="text"
                      id="filename"
                      value={customFilename}
                      onChange={(e) =>{
                        console.log("Filename changed to:", e.target.value);
                        setCustomFilename(e.target.value);  
                      }}
                      className="flex-1 focus:ring-primary-500 focus:border-primary-500 block w-full min-w-0 rounded-md sm:text-sm border-gray-300"
                      placeholder="Enter filename without extension"
                    />
                    <span className="inline-flex items-center px-3 rounded-r-md border border-l-0 border-gray-300 bg-gray-50 text-gray-500 sm:text-sm">
                      .{downloadFormat}
                    </span>
                  </div>
                </div>    

                <button
                  onClick={() => handleDownload(processedImage, downloadFormat)}
                  className="w-full inline-flex justify-center items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-secondary-600 hover:bg-secondary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-secondary-500"
                >
                  <DocumentArrowDownIcon className="-ml-1 mr-2 h-5 w-5" />
                  Download Photo
                </button>
              </div>
            )}
          </div>
        </div>
      )}
      </main>

      <footer className="bg-white mt-12">
        <div className="max-w-7xl mx-auto py-4 px-4 sm:px-6 lg:px-8">
          <p className="text-center text-sm text-gray-500">
            Passport Photo Maker - Create professional passport photos that meet international standards
          </p>
        </div>
      </footer>
    </div>
  );
}

export default App;