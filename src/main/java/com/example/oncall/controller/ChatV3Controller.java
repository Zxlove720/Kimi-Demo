package com.example.oncall.controller;

import com.example.oncall.model.ChatRequest;
import com.example.oncall.service.OnCallAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class ChatV3Controller {

    @Autowired
    private OnCallAgentService onCallAgentService;

    @GetMapping("/v3")
    public String v3Page() {
        return "v3";
    }

    @PostMapping("/v3/chat")
    @ResponseBody
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        String response = onCallAgentService.chat(request.getMessage(), request.getHistory());
        return ResponseEntity.ok(Map.of("content", response));
    }
}
