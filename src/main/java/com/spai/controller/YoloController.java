package com.spai.controller;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spai.service.YoloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Controller
public class YoloController {
    @Autowired
    private YoloService yoloService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 上传目录配置
    private static final String UPLOAD_DIR = "uploads";
    
    // 确保上传目录存在
    {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!uploadPath.toFile().exists()) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 显示上传页面（首次访问或刷新页面）
    @GetMapping("/")
    public String detectPage(){
        return "index";
    }

    // 处理上传请求并返回同一页面
    @PostMapping("/20231084039")
    public String detect(
            @RequestParam("image")MultipartFile file,
            Model model
            ){
        try {
            // 判断是图片还是视频
            String contentType = file.getContentType();
            boolean isVideo = contentType != null && contentType.startsWith("video/");
            
            if (isVideo) {
                // 保存上传的视频到 uploads 目录
                String videoName = saveUploadedFile(file);
                
                System.out.println("开始检测视频：" + videoName + ", 文件大小：" + file.getSize() / 1024 + " KB");
                
                // 视频检测
                List<List<List<Double>>> videoResults = yoloService.detectVideo(file.getBytes());
                
                // 检查是否为空结果（后端返回 500 错误时的容错处理）
                if (videoResults == null || videoResults.isEmpty()) {
                    model.addAttribute("error", "视频检测失败：后端服务暂时无法处理视频，请稍后重试或联系管理员");
                    model.addAttribute("videoName", videoName);
                } else {
                    model.addAttribute("videoResult", videoResults);
                    model.addAttribute("videoName", videoName);
                    // 将视频结果转换为 JSON 字符串，供 JavaScript 使用
                    try {
                        String videoResultJson = objectMapper.writeValueAsString(videoResults);
                        model.addAttribute("videoResultJson", videoResultJson);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                System.out.println("视频检测完成，帧数：" + (videoResults != null ? videoResults.size() : 0));
            } else {
                // 保存上传的图片到 uploads 目录
                String imageName = saveUploadedFile(file);
                
                // 图片检测
                List<List<Double>> result = yoloService.detectImage(file.getBytes());
                model.addAttribute("result", result);
                // 将结果存储在 session 中，以便后续保存
                model.addAttribute("imageName", imageName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "检测失败：" + e.getMessage());
        }
        return "index";
    }
        
    /**
     * 保存检测结果
     */
    @PostMapping("/save")
    public String saveResult(
            @RequestParam("resultData") String resultData,
            @RequestParam("imageName") String imageName,
            @RequestParam(value = "savePath", required = false) String savePath,
            Model model
    ){
        try {
            // 使用 Jackson 解析 JSON
            List<List<Double>> result = objectMapper.readValue(resultData, new TypeReference<List<List<Double>>>() {});
            String filePath = yoloService.saveResult(result, imageName, savePath);
            model.addAttribute("saveMessage", "结果已保存至：" + filePath);
            model.addAttribute("result", result);
            model.addAttribute("imageName", imageName);
        } catch (Exception e) {
            model.addAttribute("error", "保存失败：" + e.getMessage());
        }
        return "index";
    }
    
    /**
     * 处理 /save 的 GET 请求（重定向到首页）
     */
    @GetMapping("/save")
    public String saveGet() {
        return "redirect:/";
    }
    
    /**
     * 保存视频检测结果
     */
    @PostMapping("/save-video")
    public String saveVideoResult(
            @RequestParam("resultData") String resultData,
            @RequestParam("videoName") String videoName,
            @RequestParam(value = "savePath", required = false) String savePath,
            Model model
    ){
        try {
            // 使用 Jackson 解析 JSON
            List<List<List<Double>>> result = objectMapper.readValue(resultData, new TypeReference<List<List<List<Double>>>>() {});
            String filePath = yoloService.saveVideoResult(result, videoName, savePath);
            model.addAttribute("saveMessage", "结果已保存至：" + filePath);
            model.addAttribute("videoResult", result);
            model.addAttribute("videoName", videoName);
        } catch (Exception e) {
            model.addAttribute("error", "保存失败：" + e.getMessage());
        }
        return "index";
    }
    
    /**
     * 保存上传的文件并返回文件名（支持图片和视频）
     */
    private String saveUploadedFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件为空");
        }
        
        // 生成唯一的文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
            : ".jpg";
        String fileName = UUID.randomUUID().toString() + extension;
        
        // 保存到 uploads 目录
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        file.transferTo(filePath);
        
        return fileName;
    }
}
