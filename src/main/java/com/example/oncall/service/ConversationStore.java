package com.example.oncall.service;

import com.example.oncall.model.ChatMessage;
import com.example.oncall.model.Conversation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationStore {

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public Conversation createConversation(String firstMessage) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String title = firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "..." : firstMessage;
        Conversation conv = new Conversation(id, title);
        conversations.put(id, conv);
        return conv;
    }

    public Optional<Conversation> getConversation(String id) {
        return Optional.ofNullable(conversations.get(id));
    }

    public List<Conversation> listConversations() {
        return conversations.values().stream()
                .sorted((a, b) -> b.getUpdateTime().compareTo(a.getUpdateTime()))
                .map(this::toSummary)
                .toList();
    }

    public void deleteConversation(String id) {
        conversations.remove(id);
    }

    public void addMessage(String conversationId, String role, String content) {
        Conversation conv = conversations.get(conversationId);
        if (conv != null) {
            conv.addMessage(new ChatMessage(role, content));
        }
    }

    public List<ChatMessage> getMessages(String conversationId) {
        Conversation conv = conversations.get(conversationId);
        return conv != null ? new ArrayList<>(conv.getMessages()) : List.of();
    }

    private Conversation toSummary(Conversation conv) {
        Conversation summary = new Conversation();
        summary.setId(conv.getId());
        summary.setTitle(conv.getTitle());
        summary.setCreateTime(conv.getCreateTime());
        summary.setUpdateTime(conv.getUpdateTime());
        summary.setMessages(List.of());
        return summary;
    }
}
