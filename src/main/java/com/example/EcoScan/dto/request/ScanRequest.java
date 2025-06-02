package com.example.EcoScan.dto.request;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanRequest {
    private String base64ImageData;
    private String prompt;
}
