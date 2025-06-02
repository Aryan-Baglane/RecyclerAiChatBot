package com.example.EcoScan.dto.response;



import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ProductDetails {
    private String productName;
    private Double confidence;
    private String ecoTip;
    private int ecoScore;
    private List<Category> categories;
    private List<Alternative> alternatives;
    private int biodegradability;
    private int toxicity;
    private int sustainability;
    private int carbonFootprint;

//    val biodegradability: Int,
//    val toxicity : Int,
//    val sustainability: Int,
//    val carbonFootprint: Int,
}
