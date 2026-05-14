package com.example.oncall.model;

import java.util.Map;
import java.util.Set;

public class IndexedDocument {
    private String id;
    private String title;
    private String rawText;
    private String visibleText;
    private Map<String, Integer> termFreq;
    private Set<String> terms;
    private float[] embedding;

    public IndexedDocument() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getVisibleText() {
        return visibleText;
    }

    public void setVisibleText(String visibleText) {
        this.visibleText = visibleText;
    }

    public Map<String, Integer> getTermFreq() {
        return termFreq;
    }

    public void setTermFreq(Map<String, Integer> termFreq) {
        this.termFreq = termFreq;
    }

    public Set<String> getTerms() {
        return terms;
    }

    public void setTerms(Set<String> terms) {
        this.terms = terms;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
