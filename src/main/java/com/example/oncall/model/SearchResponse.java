package com.example.oncall.model;

import java.util.List;

public class SearchResponse {
    private String query;
    private List<SearchResult> results;

    public SearchResponse() {}

    public SearchResponse(String query, List<SearchResult> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    public static class SearchResult {
        private String id;
        private String title;
        private String snippet;
        private double score;

        public SearchResult() {}

        public SearchResult(String id, String title, String snippet, double score) {
            this.id = id;
            this.title = title;
            this.snippet = snippet;
            this.score = score;
        }

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

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
