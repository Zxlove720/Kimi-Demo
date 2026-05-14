package com.example.oncall.controller;

import com.example.oncall.model.DocumentRequest;
import com.example.oncall.model.IndexedDocument;
import com.example.oncall.model.SearchResponse;
import com.example.oncall.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class SearchV1Controller {

    @Autowired
    private DocumentService documentService;

    @GetMapping("/v1")
    public String v1Page() {
        return "v1";
    }

    @PostMapping("/v1/documents")
    @ResponseBody
    public ResponseEntity<Map<String, String>> addDocument(@RequestBody DocumentRequest request) {
        IndexedDocument doc = documentService.indexDocument(request.getId(), request.getHtml());
        return ResponseEntity.status(201).body(Map.of(
                "id", doc.getId(),
                "title", doc.getTitle()
        ));
    }

    @GetMapping("/v1/search")
    @ResponseBody
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "q", required = false) String q) {
        SearchResponse response = documentService.keywordSearch(q);
        return ResponseEntity.ok(response);
    }
}
