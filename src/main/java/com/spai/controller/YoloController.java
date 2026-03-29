package com.spai.controller;


import com.spai.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
public class YoloController {
    @Autowired
    private YoloService yoloService;

    // 显示上传页面（首次访问或刷新页面）
    @GetMapping("/")
    public String detectPage(){
        return "index";
    }

    // 处理上传请求并返回同一页面
    @PostMapping("/20231084039")
    public String detect(
            @RequestParam("image")MultipartFile image,
            Model model
            ){
        try {
            List<List<Double>> result = yoloService.detect(image.getBytes());
            model.addAttribute("result", result);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "index";
    }
}
