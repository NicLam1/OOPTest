package com.example.passportphotomaker.controller;

import com.example.passportphotomaker.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // For development purposes
public class PhotoController {

    private final PhotoService photoService;

    @Autowired
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/process-photo")
    public ResponseEntity<?> processPhoto(
            @RequestParam("image") MultipartFile file,
            @RequestParam(name = "format", defaultValue = "png") String format
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please upload an image");
            }

            byte[] imageBytes = photoService.processImage(file, format);

            // Determine the media type dynamically based on the format
            MediaType mediaType = format.equalsIgnoreCase("jpeg") || format.equalsIgnoreCase("jpg")
                    ? MediaType.IMAGE_JPEG
                    : MediaType.IMAGE_PNG;

            // Return processed image with correct Content-Disposition header (downloads the file)
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=passport-photo." + format)
                    .body(imageBytes);

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to process image: " + e.getMessage());
        }
    }
}
