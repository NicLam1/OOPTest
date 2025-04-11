import React, { useRef } from 'react';
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
import { backgroundColors, cropSizes } from '../constants';
import { 
  removeBackground, 
  changeBackground, 
  debouncedAdjustImage, 
  calculateInitialCrop,
  calculatePixelDimensions,
  cropImage
} from '../utils/imageHandlers';

/**
 * Step 1: Upload & Remove Background 
 */
export const UploadStep = ({ 
  selectedFile, setSelectedFile, 
  imageUrl, setImageUrl, 
  selectedSize, setSelectedSize,
  downloadFormat, setDownloadFormat,
  loading, setLoading,
  setError,
  setBackgroundRemovedImage,
  setBackgroundRemovedFile,
  setStep
}) => {

  const handleFileChange = (event) => {
    const file = event.target.files[0];
    setSelectedFile(file);
    setImageUrl(URL.createObjectURL(file));
    setError(null);
  };

  const handleRemoveBackground = async () => {
    if (!selectedFile) {
      setError("Please select an image first.");
      return;
    }

    try {
      setLoading(true);
      const { imageUrl, imageFile } = await removeBackground(selectedFile, downloadFormat);
      
      setBackgroundRemovedImage(imageUrl);
      setBackgroundRemovedFile(imageFile);
      setStep(2);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
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
                className="mx-auto max-h-64 shadow-md"
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
  );
};

/**
 * Step 2: Background Changing
 */
export const BackgroundStep = ({
  backgroundRemovedImage,
  backgroundChangedImage, setBackgroundChangedImage,
  backgroundImage, setBackgroundImage,
  backgroundType, setBackgroundType,
  selectedBackgroundColor, setSelectedBackgroundColor,
  brightness, setBrightness,
  contrast, setContrast,
  saturation, setSaturation,
  backgroundRemovedFile, setBackgroundRemovedFile,
  downloadFormat,
  loading, setLoading, setError,
  setFinalAdjustedImage,
  setStep
}) => {
  const [isPickingColor, setIsPickingColor] = React.useState(false);
  const [backgroundScale, setBackgroundScale] = React.useState(1.0);
  const [backgroundOffsetX, setBackgroundOffsetX] = React.useState(0.0);
  const [backgroundOffsetY, setBackgroundOffsetY] = React.useState(0.0);

  const handleBackgroundImageChange = (event) => {
    const file = event.target.files[0];
    if (file) {
      setBackgroundImage(file);
      setBackgroundType('image');
    }
  };

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
      
      const options = {
        downloadFormat,
        backgroundType,
        backgroundColor: backgroundType === 'color' ? selectedBackgroundColor : null,
        backgroundImage: backgroundType === 'image' ? backgroundImage : null,
        backgroundScale,
        backgroundOffsetX,
        backgroundOffsetY
      };

      const { imageUrl, imageFile } = await changeBackground(imageBlob, options);
  
      setBackgroundChangedImage(imageUrl);
      setFinalAdjustedImage(imageUrl);
      setBackgroundRemovedFile(imageFile);

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

  return (
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
            <label className="block text-sm font-medium text-gray-700 mb-2">Custom Color </label>
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
                </div>
                
                {/* Position Controls */}
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
                </div>
                
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
                </div>
              </div>
            )}

            {/* Upload Background Image */}
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
                </div>
              </div>
            </div>
          </>
        )}

        <div className="mt-6">
          <button
            onClick={handleChangeBackground}
            disabled={!backgroundRemovedImage || loading}
            className={`inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white ${
              !backgroundRemovedImage || loading
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
  );
};

/**
 * Step 3: Adjust Photo
 */
export const AdjustStep = ({
  backgroundChangedImage,
  finalAdjustedImage, setFinalAdjustedImage,
  brightness, setBrightness,
  contrast, setContrast,
  saturation, setSaturation,
  backgroundRemovedFile, setBackgroundRemovedFile,
  downloadFormat,
  loading, setLoading,
  setStep
}) => {

  const handleAdjustment = async (key, value) => {
    // Update state
    if (key === 'brightness') setBrightness(value);
    if (key === 'contrast') setContrast(value);
    if (key === 'saturation') setSaturation(value);
  
    // Call the debounced function with the updated values
    const result = await debouncedAdjustImage(
      backgroundRemovedFile, 
      {
        brightness: key === 'brightness' ? value : brightness,
        contrast: key === 'contrast' ? value : contrast,
        saturation: key === 'saturation' ? value : saturation,
      },
      downloadFormat
    );
    
    setFinalAdjustedImage(result.imageUrl);
    setBackgroundRemovedFile(result.imageFile);
  };

  return (
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
                className="max-h-64 shadow-sm"
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

          {/* Reset Button */}
          <button
            type="button"
            onClick={() => {
              setBrightness(0);
              setContrast(1);
              setSaturation(1);
              handleAdjustment('brightness', 0);
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
  );
};

/**
 * Step 4: Crop & Process
 */
export const CropStep = ({
  finalAdjustedImage,
  cropSizes,
  selectedSize,
  downloadFormat,
  processedImage, setProcessedImage,
  loading, setLoading,
  setError,
  customFilename, setCustomFilename,
  imageSize, setImageSize
}) => {
  const [crop, setCrop] = React.useState({
    unit: '%',
    width: 90,
    height: 90,
    x: 5,
    y: 5
  });
  const [completedCrop, setCompletedCrop] = React.useState(null);
  const imgRef = useRef(null);

  const handleImageLoad = (e) => {
    const { naturalWidth, naturalHeight } = e.target;
    
    // Store image dimensions for future reference
    setImageSize({ width: naturalWidth, height: naturalHeight });
    
    const aspectRatio = cropSizes[selectedSize].width / cropSizes[selectedSize].height;
    const initialCrop = calculateInitialCrop(naturalWidth, naturalHeight, aspectRatio);
    
    setCrop(initialCrop);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!finalAdjustedImage || !completedCrop) {
      setError("Please complete background removal and cropping first.");
      return;
    }

    try {
      setLoading(true);
      
      // Calculate the target dimensions based on the selected format
      const targetDimensions = calculatePixelDimensions(
        cropSizes[selectedSize].width,
        cropSizes[selectedSize].height,
        cropSizes[selectedSize].unit
      );

      const { imageUrl } = await cropImage(
        imgRef.current,
        completedCrop,
        targetDimensions,
        downloadFormat
      );
      
      setProcessedImage(imageUrl);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = (url, format) => {
    const filename = customFilename.trim() || 'passport-photo';
    const link = document.createElement('a');
    link.href = url;
    link.download = `${filename}.${format}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
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
                className="mx-auto max-h-96"
              />
            </div>
            
            <div className="text-sm text-gray-500 mb-4">
              <p>
                Final size: {cropSizes[selectedSize].width}×{cropSizes[selectedSize].height} {cropSizes[selectedSize].unit}.
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
                  onChange={(e) => setCustomFilename(e.target.value)}
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
  );
};