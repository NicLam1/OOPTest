import { API_ENDPOINTS } from '../constants';
import { debounce } from 'lodash';

/**
 * Removes background from the uploaded image
 */
export const removeBackground = async (file, format) => {
  if (!file) {
    throw new Error("Please select an image first.");
  }

  const formData = new FormData();
  formData.append('image', file);  // Match the @RequestParam("image") in controller
  formData.append('format', format || 'png');  // Add format

  const response = await fetch(API_ENDPOINTS.PROCESS_PHOTO, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Server responded with ${response.status}`);
  }

  const resultBlob = await response.blob();
  const imageFile = new File([resultBlob], "transparent.png", { type: resultBlob.type });

  return {
    imageUrl: URL.createObjectURL(imageFile),
    imageFile
  };
};

/**
 * Changes the background of an image
 */
export const changeBackground = async (imageBlob, options) => {
  const { 
    downloadFormat = 'png', 
    backgroundType, 
    backgroundColor, 
    backgroundImage,
    backgroundScale = 1.0,
    backgroundOffsetX = 0.0,
    backgroundOffsetY = 0.0 
  } = options;

  const formData = new FormData();
  formData.append('image', imageBlob, 'transparent-image.png');
  formData.append('format', downloadFormat);
  
  // Add either background color or image based on selected type
  if (backgroundType === 'color') {
    formData.append('backgroundColor', backgroundColor);
  } else if (backgroundType === 'image' && backgroundImage) {
    formData.append('backgroundImg', backgroundImage);
    // Add background resizing parameters
    formData.append('bgScale', backgroundScale);
    formData.append('bgOffsetX', backgroundOffsetX);
    formData.append('bgOffsetY', backgroundOffsetY);
  }

  const response = await fetch(API_ENDPOINTS.PROCESS_PHOTO, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Server responded with ${response.status}`);
  }

  const resultBlob = await response.blob();
  const bgChangedFile = new File([resultBlob], "bg-changed.png", { type: resultBlob.type });
  
  return {
    imageUrl: URL.createObjectURL(resultBlob),
    imageFile: bgChangedFile
  };
};

/**
 * Adjusts photo brightness, contrast and saturation
 */
export const adjustImage = async (imageFile, adjustments, format = 'png') => {
  const { brightness, contrast, saturation } = adjustments;

  const formData = new FormData();
  formData.append("image", imageFile);
  formData.append("brightness", brightness);
  formData.append("contrast", contrast);
  formData.append("saturation", saturation);
  formData.append("format", format);

  const response = await fetch(API_ENDPOINTS.ADJUST_PHOTO, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Adjustment request failed: ${response.status}`);
  }

  const adjustedBlob = await response.blob();
  const adjustedFile = new File([adjustedBlob], "adjusted.png", { type: adjustedBlob.type });
  
  return {
    imageUrl: URL.createObjectURL(adjustedBlob),
    imageFile: adjustedFile
  };
};

/**
 * Debounced version of the adjust image function
 */
export const debouncedAdjustImage = debounce(adjustImage, 300);

/**
 * Handles crop calculations and image crop operations
 */
export const cropImage = async (image, crop, targetDimensions, format = 'png') => {
  const canvas = document.createElement('canvas');
  const scaleX = image.naturalWidth / image.width;
  const scaleY = image.naturalHeight / image.height;
  
  // Set canvas dimensions to match the target size exactly
  canvas.width = targetDimensions.width;
  canvas.height = targetDimensions.height;
  const ctx = canvas.getContext('2d');
  
  // Enable image smoothing for better quality
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';

  // Calculate the actual source dimensions in the original image
  const sourceX = Math.round(crop.x * scaleX);
  const sourceY = Math.round(crop.y * scaleY);
  const sourceWidth = Math.round(crop.width * scaleX);
  const sourceHeight = Math.round(crop.height * scaleY);

  // Draw the cropped image directly to the target size
  ctx.drawImage(
    image,
    sourceX,
    sourceY,
    sourceWidth,
    sourceHeight,
    0,
    0,
    targetDimensions.width,
    targetDimensions.height
  );

  return new Promise(resolve => {
    canvas.toBlob(blob => {
      resolve({
        imageUrl: URL.createObjectURL(blob),
        imageBlob: blob
      });
    }, `image/${format}`, 1.0);
  });
};

/**
 * Calculate pixel dimensions from physical dimensions and DPI
 */
export const calculatePixelDimensions = (width, height, unit, dpi = 300) => {
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

/**
 * Calculate initial crop based on image dimensions and aspect ratio
 */
export const calculateInitialCrop = (imageWidth, imageHeight, aspectRatio) => {
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
  
  return {
    unit: 'px',
    width: cropWidth,
    height: cropHeight,
    x: x,
    y: y
  };
};