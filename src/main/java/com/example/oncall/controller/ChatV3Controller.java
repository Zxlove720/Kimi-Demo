package com.example.oncall.controller;

import com.example.oncall.model.ChatRequest;
import com.example.oncall.model.ChatResponse;
import com.example.oncall.model.Conversation;
import com.example.oncall.service.ConversationStore;
import com.example.oncall.service.OnCallAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ChatV3Controller {

    @Autowired
    private OnCallAgentService onCallAgentService;

    @Autowired
    private ConversationStore conversationStore;

    @GetMapping("/v3")
    public String v3Page() {
        return "v3";
    }

    @PostMapping("/v3/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = onCallAgentService.chat(request.getConversationId(), request.getMessage());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/v3/conversations")
    @ResponseBody
    public ResponseEntity<List<Conversation>> listConversations() {
        return ResponseEntity.ok(conversationStore.listConversations());
    }

    @GetMapping("/v3/conversations/{id}")
    @ResponseBody
    public ResponseEntity<Conversation> getConversation(@PathVariable String id) {
        return conversationStore.getConversation(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/v3/conversations/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        conversationStore.deleteConversation(id);
        return ResponseEntity.ok().build();
    }
}
