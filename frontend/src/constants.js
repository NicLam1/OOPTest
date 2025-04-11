// Photo format constants
export const cropSizes = {
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
  export const DPI = 300;
  
  // Background color presets
  export const backgroundColors = [
    { name: 'White', value: '#ffffff' },
    { name: 'Blue', value: '#0284c7' },
    { name: 'Red', value: '#dc2626' },
    { name: 'Gray', value: '#9ca3af' },
    { name: 'Black', value: '#000000' },
  ];
  
  // API endpoints
  export const API_ENDPOINTS = {
    PROCESS_PHOTO: 'http://localhost:8080/api/process-photo',
    ADJUST_PHOTO: 'http://localhost:8080/api/adjust-photo'
  };