package com.example.pythonvoicedemo;

public class RecognitionResponse {
    private int predicted_label;
    private float confidence;

    public int getPredictedLabel() { return predicted_label; }
    public float getConfidence() { return confidence; }
}
