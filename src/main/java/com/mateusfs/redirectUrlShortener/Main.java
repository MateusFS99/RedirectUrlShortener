package com.mateusfs.redirectUrlShortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Client s3Client = S3Client.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String pathParams = input.get("body").toString();
        String shortUrlCode = pathParams.replace("/", "");

        if(shortUrlCode == null || shortUrlCode.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: 'shortUrlCode' is required.");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket("mfs-shortener-storage").key(shortUrlCode + ".json").build();
        InputStream s3ObjectStream;

        try {
            s3ObjectStream = s3Client.getObject(getObjectRequest);
        } catch (Exception exception) {
            throw new RuntimeException("Error fetching URL data from S3: " + exception.getMessage());
        }

        UrlData urlData;

        try {
            urlData = objectMapper.readValue(s3ObjectStream, UrlData.class);
        } catch (Exception exception) {
            throw new RuntimeException("Error deserializing URL data: " + exception.getMessage());
        }

        long currentTime = System.currentTimeMillis() / 1000;

        Map<String, Object> response = new HashMap<>();

        if(urlData.getExpirationTime() < currentTime) {
            response.put("statusCode", 410);
            response.put("body", "This URL has expired.");

            return response;
        }

        Map<String, String> headers = new HashMap<>();

        response.put("statusCode", 302);
        headers.put("location", urlData.getOriginalUrl());
        response.put("headers", headers);

        return response;
    }
}