package com.example.oncall.model;

public class ChatResponse {
    private String conversationId;
    private String content;
    private String reasoning;

    public ChatResponse() {}

    public ChatResponse(String conversationId, String content) {
        this.conversationId = conversationId;
        this.content = content;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
