package com.spai.service;



import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class YoloService {
    RestTemplate restTemplate = new RestTemplate();
    ObjectMapper objectMapper = new ObjectMapper();

    @Value("${yolo.api.url}")
    private String yoloApiUrl;
    
    @Value("${detection.save.path:}")
    private String savePathConfig;

    public List<List<Double>> detect(byte[] image){
        return detectImage(image);
    }
    
    /**
     * 检测图片
     */
    public List<List<Double>> detectImage(byte[] image){
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
    
    /**
     * 检测视频
     */
    public List<List<List<Double>>> detectVideo(byte[] video){
        System.out.println("开始调用后端 API 进行视频检测，数据大小：" + (video.length / 1024) + " KB");
        
        try {
            // 构建请求头和请求体
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image",new ByteArrayResource(video){
                @Override
                public String getFilename() {
                    return "video.mp4";
                }
            });
            HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            System.out.println("正在发送请求到：" + yoloApiUrl + "/20231084039");
            
            // 先尝试获取原始响应，看看后端返回什么
            ResponseEntity<String> stringResponse = restTemplate.exchange(
                    yoloApiUrl + "/20231084039",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            
            System.out.println("API 响应状态码：" + stringResponse.getStatusCode());
            System.out.println("API 原始响应内容：" + stringResponse.getBody());
            
            // 如果响应为空或错误，返回空列表
            if (stringResponse.getBody() == null || stringResponse.getBody().trim().isEmpty()) {
                System.out.println("响应为空，返回空列表");
                return new java.util.ArrayList<>();
            }
            
            // 尝试解析 JSON
            try {
                return objectMapper.readValue(stringResponse.getBody(), new TypeReference<List<List<List<Double>>>>(){});
            } catch (Exception e) {
                System.err.println("JSON 解析失败：" + e.getMessage());
                System.err.println("后端返回的格式可能不正确");
                
                // 尝试解析为其他可能的格式
                try {
                    // 可能是单个检测结果对象或其他格式
                    System.out.println("尝试检测后端返回的实际格式...");
                    com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(stringResponse.getBody());
                    System.out.println("后端返回的 JSON 类型：" + jsonNode.getNodeType());
                    System.out.println("完整 JSON 内容：" + jsonNode.toPrettyString());
                    
                    // 如果是对象，可能包含 results 字段
                    if (jsonNode.isObject() && jsonNode.has("results")) {
                        System.out.println("检测到 JSON 对象，包含 results 字段，开始解析...");
                        JsonNode resultsNode = jsonNode.get("results");
                        List<List<List<Double>>> videoResults = new java.util.ArrayList<>();
                        
                        // 遍历每一帧
                        for (JsonNode frameNode : resultsNode) {
                            if (frameNode.has("detections")) {
                                JsonNode detectionsNode = frameNode.get("detections");
                                List<List<Double>> frameDetections = new java.util.ArrayList<>();
                                
                                // 遍历每个检测结果
                                for (JsonNode detection : detectionsNode) {
                                    if (detection.has("bbox") && detection.has("confidence") && detection.has("class")) {
                                        JsonNode bboxNode = detection.get("bbox");
                                        // 构建标准格式：[x1, y1, x2, y2, confidence, class]
                                        List<Double> detectionData = new java.util.ArrayList<>();
                                        detectionData.add(bboxNode.get(0).asDouble()); // x1
                                        detectionData.add(bboxNode.get(1).asDouble()); // y1
                                        detectionData.add(bboxNode.get(2).asDouble()); // x2
                                        detectionData.add(bboxNode.get(3).asDouble()); // y2
                                        detectionData.add(detection.get("confidence").asDouble()); // confidence
                                        detectionData.add((double) detection.get("class").asInt()); // class
                                        
                                        frameDetections.add(detectionData);
                                    }
                                }
                                
                                videoResults.add(frameDetections);
                                System.out.println("解析第 " + videoResults.size() + " 帧，检测到 " + frameDetections.size() + " 个目标");
                            }
                        }
                        
                        System.out.println("视频解析完成，总帧数：" + videoResults.size());
                        return videoResults;
                    }
                } catch (Exception ex) {
                    System.err.println("无法解析 JSON 结构：" + ex.getMessage());
                    ex.printStackTrace();
                }
                
                return new java.util.ArrayList<>();
            }
            
        } catch (Exception e) {
            System.err.println("视频检测失败：" + e.getMessage());
            e.printStackTrace();
            
            // 如果是 500 错误或其他错误，返回空列表
            if (e.getMessage().contains("500") || e.getMessage().contains("JSON")) {
                System.err.println("后端服务返回错误或 JSON 解析失败");
                return new java.util.ArrayList<>();
            }
            
            throw new RuntimeException("视频检测失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 保存检测结果到文件
     * @param result 检测结果
     * @param originalFilename 原始文件名
     * @return 保存的文件路径
     */
    public String saveResult(List<List<Double>> result, String originalFilename) throws IOException {
        return saveResult(result, originalFilename, null);
    }
    
    /**
     * 保存检测结果到指定位置
     * @param result 检测结果
     * @param originalFilename 原始文件名
     * @param customPath 自定义保存路径（可选）
     * @return 保存的文件路径
     */
    public String saveResult(List<List<Double>> result, String originalFilename, String customPath) throws IOException {
        Path savePath;
        
        // 优先使用自定义路径，其次使用配置文件路径，最后使用默认路径
        if (customPath != null && !customPath.trim().isEmpty()) {
            savePath = Paths.get(customPath);
        } else if (savePathConfig != null && !savePathConfig.trim().isEmpty()) {
            savePath = Paths.get(savePathConfig);
        } else {
            savePath = Paths.get("detection_results");
        }
        
        // 创建保存目录
        if (!savePath.toFile().exists()) {
            savePath.toFile().mkdirs();
        }
        
        // 生成文件名：原名_时间戳.txt
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String baseName = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
            : (originalFilename != null ? originalFilename : "result");
        String fileName = baseName + "_" + timestamp + ".txt";
        Path filePath = savePath.resolve(fileName);
        
        // 写入结果到文件
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write("目标检测结果\n");
            writer.write("检测时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("检测到目标数量：" + result.size() + "\n");
            writer.write("\n详细信息:\n");
            writer.write("-------------------------------------------------------------------\n");
            writer.write(String.format("%-6s %-10s %-12s %-45s\n", "序号", "类别", "置信度", "坐标 (x1, y1, x2, y2)"));
            writer.write("-------------------------------------------------------------------\n");
            
            for (int i = 0; i < result.size(); i++) {
                List<Double> detection = result.get(i);
                String className = detection.size() > 5 ? detection.get(5).toString() : "N/A";
                Double confidence = detection.size() > 4 ? detection.get(4) : 0.0;
                String coordinates = String.format("(%.2f, %.2f, %.2f, %.2f)",
                    detection.size() > 0 ? detection.get(0) : 0,
                    detection.size() > 1 ? detection.get(1) : 0,
                    detection.size() > 2 ? detection.get(2) : 0,
                    detection.size() > 3 ? detection.get(3) : 0
                );
                writer.write(String.format("%-6d %-10s %-12.4f %-45s\n", 
                    i + 1, className, confidence, coordinates));
            }
            writer.write("-------------------------------------------------------------------\n");
        }
        
        return filePath.toString();
    }
    
    /**
     * 保存视频检测结果到文件
     * @param result 检测结果（每帧的检测结果）
     * @param originalFilename 原始文件名
     * @param customPath 自定义保存路径（可选）
     * @return 保存的文件路径
     */
    public String saveVideoResult(List<List<List<Double>>> result, String originalFilename, String customPath) throws IOException {
        Path savePath;
        
        // 优先使用自定义路径，其次使用配置文件路径，最后使用默认路径
        if (customPath != null && !customPath.trim().isEmpty()) {
            savePath = Paths.get(customPath);
        } else if (savePathConfig != null && !savePathConfig.trim().isEmpty()) {
            savePath = Paths.get(savePathConfig);
        } else {
            savePath = Paths.get("detection_results");
        }
        
        // 创建保存目录
        if (!savePath.toFile().exists()) {
            savePath.toFile().mkdirs();
        }
        
        // 生成文件名：原名_时间戳.txt
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String baseName = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
            : (originalFilename != null ? originalFilename : "video_result");
        String fileName = baseName + "_" + timestamp + ".txt";
        Path filePath = savePath.resolve(fileName);
        
        // 写入结果到文件
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write("视频目标检测结果\n");
            writer.write("检测时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("总帧数：" + result.size() + "\n");
            
            // 统计有目标的帧数
            long framesWithObjects = result.stream().filter(frame -> !frame.isEmpty()).count();
            writer.write("有目标的帧数：" + framesWithObjects + "\n");
            writer.write("\n详细信息:\n");
            writer.write("===================================================================\n\n");
            
            for (int i = 0; i < result.size(); i++) {
                List<List<Double>> frame = result.get(i);
                writer.write("-------------------------------------------------------------------\n");
                writer.write("第 " + (i + 1) + " 帧\n");
                
                if (frame.isEmpty()) {
                    writer.write("未检测到目标\n");
                } else {
                    writer.write("检测到目标数量：" + frame.size() + "\n");
                    writer.write(String.format("%-6s %-10s %-12s %-45s\n", "序号", "类别", "置信度", "坐标 (x1, y1, x2, y2)"));
                    
                    for (int j = 0; j < frame.size(); j++) {
                        List<Double> detection = frame.get(j);
                        String className = detection.size() > 5 ? detection.get(5).toString() : "N/A";
                        Double confidence = detection.size() > 4 ? detection.get(4) : 0.0;
                        String coordinates = String.format("(%.2f, %.2f, %.2f, %.2f)",
                            detection.size() > 0 ? detection.get(0) : 0,
                            detection.size() > 1 ? detection.get(1) : 0,
                            detection.size() > 2 ? detection.get(2) : 0,
                            detection.size() > 3 ? detection.get(3) : 0
                        );
                        writer.write(String.format("%-6d %-10s %-12.4f %-45s\n", 
                            j + 1, className, confidence, coordinates));
                    }
                }
                writer.write("\n");
            }
            writer.write("===================================================================\n");
        }
        
        return filePath.toString();
    }
}
