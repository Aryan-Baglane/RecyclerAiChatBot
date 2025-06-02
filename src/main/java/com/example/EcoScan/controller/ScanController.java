package com.example.EcoScan.controller;


import com.example.EcoScan.dto.request.ScanRequest;
import com.example.EcoScan.dto.response.ProductDetails;
import com.example.EcoScan.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Base64;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {
    private final ScanService scanService;
    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductDetails> scanProductMultipart(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "prompt", required = false) String prompt) {

        try {
            logger.info("Received multipart file: {} ({} bytes)",
                    image.getOriginalFilename(), image.getSize());

            if (image.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
            return ResponseEntity.ok(scanService.scanProduct(
                    new ScanRequest(base64Image, prompt)));

        } catch (IOException e) {
            logger.error("File processing error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductDetails> scanProductJson(
            @RequestBody @Valid ScanRequest request) {
        logger.info("Received JSON request");
        return ResponseEntity.ok(scanService.scanProduct(request));
    }
}