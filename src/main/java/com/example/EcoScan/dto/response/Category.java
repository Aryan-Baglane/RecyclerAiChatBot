package com.example.EcoScan.dto.response;



import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Category {
    private String title;
    private String score;
    private String description;
    private List<String> impactDetails;
}
