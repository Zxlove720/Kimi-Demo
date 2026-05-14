package com.example.oncall.controller;

import com.example.oncall.model.SearchResponse;
import com.example.oncall.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SearchV2Controller {

    @Autowired
    private DocumentService documentService;

    @GetMapping("/v2")
    public String v2Page() {
        return "v2";
    }

    @GetMapping("/v2/search")
    @ResponseBody
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "q", required = false) String q) {
        SearchResponse response = documentService.semanticSearch(q);
        return ResponseEntity.ok(response);
    }
}
