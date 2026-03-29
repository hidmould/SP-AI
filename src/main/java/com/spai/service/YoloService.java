package com.spai.service;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;


@Service
public class YoloService {
    RestTemplate restTemplate = new RestTemplate();

    @Value("${yolo.api.url}")
    private String yoloApiUrl;

    public List<List<Double>> detect(byte[] image){
        // 构建请求头和请求体
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image",new ByteArrayResource(image){
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        });
        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(
                yoloApiUrl + "/20231084039",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<List<List<Double>>>(){}
        ).getBody();
    }
}
