package com.example.oncall.model;

public class ChatResponse {
    private String content;
    private String reasoning;

    public ChatResponse() {}

    public ChatResponse(String content, String reasoning) {
        this.content = content;
        this.reasoning = reasoning;
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
