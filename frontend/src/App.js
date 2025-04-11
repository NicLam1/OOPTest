import React, { useState } from 'react';
import { PhotoIcon } from '@heroicons/react/24/outline';
import { UploadStep, BackgroundStep, AdjustStep, CropStep } from './components/Steps';
import { cropSizes } from './constants';


function App() {
  // Core application state
  const [selectedFile, setSelectedFile] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [processedImage, setProcessedImage] = useState(null);
  const [backgroundRemovedImage, setBackgroundRemovedImage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedSize, setSelectedSize] = useState('35x45'); // default
  const [downloadFormat, setDownloadFormat] = useState('png');
  const [step, setStep] = useState(1); // 1: Upload & Remove BG, 2: Change BG, 3: Adjust Photo, 4: Crop & Process
  
  // Background change state
  const [backgroundChangedImage, setBackgroundChangedImage] = useState(null);
  const [backgroundImage, setBackgroundImage] = useState(null);
  const [backgroundType, setBackgroundType] = useState('color');
  const [selectedBackgroundColor, setSelectedBackgroundColor] = useState('#ffffff');
  
  // Adjustment state
  const [brightness, setBrightness] = useState(0);
  const [contrast, setContrast] = useState(1);
  const [saturation, setSaturation] = useState(1);
  const [backgroundRemovedFile, setBackgroundRemovedFile] = useState(null);
  const [finalAdjustedImage, setFinalAdjustedImage] = useState(null);
  
  // Download state
  const [customFilename, setCustomFilename] = useState('passport-photo');

  // Crop state
  const [imageSize, setImageSize] = useState({ width: 0, height: 0 });

  // Progress steps component
  const ProgressSteps = () => (
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
  );

  // Error message component
  const ErrorMessage = ({ message }) => (
    <div className="rounded-md bg-red-50 p-4 mx-4 mb-6">
      <div className="flex">
        <div className="flex-shrink-0">
          <svg className="h-5 w-5 text-red-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
          </svg>
        </div>
        <div className="ml-3">
          <p className="text-sm font-medium text-red-800">{message}</p>
        </div>
      </div>
    </div>
  );

  return (
    // <div className="min-h-screen flex flex-col bg-gray-50 align-items-center justify-center">

    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
          <h1 className="text-3xl font-bold text-gray-900 flex items-center">
            <PhotoIcon className="h-8 w-8 mr-2 text-primary-600" />
            Passport Photo Maker
          </h1>
        </div>
      </header>

      <main className="flex-grow max-w-5xl w-full mx-auto px-4 pt-4 pb-2">
      {/* <main className="flex-grow max-w-5xl mx-auto py-6 px-4 sm:px-6 lg:px-8 sticky top-0 justify-center items-center"> */}


      {/* <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"> */}
        {/* Progress Steps */}
        <ProgressSteps />

        {/* Error Display */}
        {error && <ErrorMessage message={error} />}

        {/* Step Components */}
        {step === 1 && (
          <UploadStep
            selectedFile={selectedFile}
            setSelectedFile={setSelectedFile}
            imageUrl={imageUrl}
            setImageUrl={setImageUrl}
            selectedSize={selectedSize}
            setSelectedSize={setSelectedSize}
            downloadFormat={downloadFormat}
            setDownloadFormat={setDownloadFormat}
            loading={loading}
            setLoading={setLoading}
            setError={setError}
            setBackgroundRemovedImage={setBackgroundRemovedImage}
            setBackgroundRemovedFile={setBackgroundRemovedFile}
            setStep={setStep}
          />
        )}

        {step === 2 && (
          <BackgroundStep
            backgroundRemovedImage={backgroundRemovedImage}
            backgroundChangedImage={backgroundChangedImage}
            setBackgroundChangedImage={setBackgroundChangedImage}
            backgroundImage={backgroundImage}
            setBackgroundImage={setBackgroundImage}
            backgroundType={backgroundType}
            setBackgroundType={setBackgroundType}
            selectedBackgroundColor={selectedBackgroundColor}
            setSelectedBackgroundColor={setSelectedBackgroundColor}
            brightness={brightness}
            setBrightness={setBrightness}
            contrast={contrast}
            setContrast={setContrast}
            saturation={saturation}
            setSaturation={setSaturation}
            backgroundRemovedFile={backgroundRemovedFile}
            setBackgroundRemovedFile={setBackgroundRemovedFile}
            downloadFormat={downloadFormat}
            loading={loading}
            setLoading={setLoading}
            setError={setError}
            setFinalAdjustedImage={setFinalAdjustedImage}
            setStep={setStep}
          />
        )}

        {step === 3 && (
          <AdjustStep
            backgroundChangedImage={backgroundChangedImage}
            finalAdjustedImage={finalAdjustedImage}
            setFinalAdjustedImage={setFinalAdjustedImage}
            brightness={brightness}
            setBrightness={setBrightness}
            contrast={contrast}
            setContrast={setContrast}
            saturation={saturation}
            setSaturation={setSaturation}
            backgroundRemovedFile={backgroundRemovedFile}
            setBackgroundRemovedFile={setBackgroundRemovedFile}
            downloadFormat={downloadFormat}
            loading={loading}
            setLoading={setLoading}
            setStep={setStep}
          />
        )}

        {step === 4 && (
          <CropStep
            finalAdjustedImage={finalAdjustedImage}
            cropSizes={cropSizes}
            selectedSize={selectedSize}
            downloadFormat={downloadFormat}
            processedImage={processedImage}
            setProcessedImage={setProcessedImage}
            loading={loading}
            setLoading={setLoading}
            setError={setError}
            customFilename={customFilename}
            setCustomFilename={setCustomFilename}
            imageSize={imageSize}
            setImageSize={setImageSize}
          />
        )}
      </main>

      <footer className="bg-white text-sm text-gray-500 text-center py-2 border-t">
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