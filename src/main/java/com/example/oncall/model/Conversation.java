package com.example.oncall.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private String id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ChatMessage> messages = new ArrayList<>();

    public Conversation() {}

    public Conversation(String id, String title) {
        this.id = id;
        this.title = title;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.updateTime = LocalDateTime.now();
    }
}
