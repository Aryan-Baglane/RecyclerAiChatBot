package com.example.EcoScan.dto.response;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Alternative {
    private String productName;
    private List<String> features;
    private String amazonLink;
    private int ecoScore;
}

