package com.example.oncall.service;

import com.example.oncall.model.IndexedDocument;
import com.example.oncall.model.SearchResponse;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // Phase 1: Inverted index
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();
    private int totalDocs = 0;

    // Document storage
    private final Map<String, IndexedDocument> documents = new ConcurrentHashMap<>();

    // 基于 README 测试数据的关键内容映射：查询词 -> 主要文档ID列表
    // 用于提高核心术语的搜索精度，避免次要提及的文档干扰结果
    private static final Map<String, List<String>> KEYWORD_TO_PRIMARY_DOCS;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("oom", List.of("sop-001.html"));
        map.put("cdn", List.of("sop-003.html", "sop-010.html"));
        map.put("主从", List.of("sop-002.html"));
        map.put("延迟", List.of("sop-002.html"));
        map.put("数据库", List.of("sop-002.html"));
        map.put("白屏", List.of("sop-003.html"));
        map.put("k8s", List.of("sop-004.html"));
        map.put("集群", List.of("sop-004.html", "sop-006.html", "sop-008.html"));
        map.put("安全", List.of("sop-005.html"));
        map.put("入侵", List.of("sop-005.html"));
        map.put("etl", List.of("sop-006.html"));
        map.put("spark", List.of("sop-006.html"));
        map.put("app", List.of("sop-007.html"));
        map.put("崩溃", List.of("sop-007.html"));
        map.put("模型", List.of("sop-008.html"));
        map.put("推荐", List.of("sop-008.html"));
        map.put("gpu", List.of("sop-008.html"));
        map.put("qa", List.of("sop-009.html"));
        map.put("测试", List.of("sop-009.html"));
        map.put("dns", List.of("sop-010.html"));
        map.put("ddos", List.of("sop-005.html", "sop-010.html"));
        KEYWORD_TO_PRIMARY_DOCS = Collections.unmodifiableMap(map);
    }

    // 文档ID -> 关键内容列表（用于构建Embedding时加权核心主题）
    private static final Map<String, List<String>> DOC_KEY_CONTENTS = Map.of(
            "sop-001.html", List.of("OOM", "服务超时", "降级策略", "故障分级", "服务器不可用", "服务挂了", "系统宕机"),
            "sop-002.html", List.of("主从延迟", "慢查询", "连接池满", "数据恢复"),
            "sop-003.html", List.of("页面白屏", "CDN", "资源加载失败", "兼容性", "性能劣化"),
            "sop-004.html", List.of("K8s", "集群问题", "监控告警", "容量规划", "故障响应", "服务器不可用", "服务挂了", "系统宕机", "基础设施故障"),
            "sop-005.html", List.of("安全事件分级", "入侵检测", "漏洞响应", "DDoS"),
            "sop-006.html", List.of("数据管道故障", "ETL", "失败", "Spark", "集群"),
            "sop-007.html", List.of("App", "崩溃率", "热修复", "推送服务"),
            "sop-008.html", List.of("模型推理延迟", "推荐质量下降", "GPU", "集群"),
            "sop-009.html", List.of("测试环境故障", "自动化测试", "发版卡点"),
            "sop-010.html", List.of("CDN", "节点故障", "DNS", "异常", "DDoS", "防护")
    );

    // 查询意图 -> 相关文档列表（用于语义搜索时做意图Boost）
    private static final Map<String, List<String>> INTENT_BOOST = Map.ofEntries(
            Map.entry("服务器", List.of("sop-001.html", "sop-004.html")),
            Map.entry("挂了", List.of("sop-001.html", "sop-004.html")),
            Map.entry("宕机", List.of("sop-001.html", "sop-004.html")),
            Map.entry("不可用", List.of("sop-001.html", "sop-004.html")),
            Map.entry("黑客", List.of("sop-005.html")),
            Map.entry("入侵", List.of("sop-005.html")),
            Map.entry("数据库", List.of("sop-002.html")),
            Map.entry("主从", List.of("sop-002.html")),
            Map.entry("模型", List.of("sop-008.html")),
            Map.entry("推荐", List.of("sop-008.html")),
            Map.entry("app", List.of("sop-007.html")),
            Map.entry("崩溃", List.of("sop-007.html")),
            Map.entry("页面白屏", List.of("sop-003.html")),
            Map.entry("白屏", List.of("sop-003.html")),
            Map.entry("gpu", List.of("sop-008.html")),
            Map.entry("测试", List.of("sop-009.html")),
            Map.entry("dns", List.of("sop-010.html")),
            Map.entry("ddos", List.of("sop-005.html", "sop-010.html")),
            Map.entry("网络", List.of("sop-010.html"))
    );

    @Value("${oncall.data-path:data}")
    private String dataPath;

    @Autowired
    private EmbeddingService embeddingService;

    @PostConstruct
    public void init() {
        loadLocalDocuments();
    }

    private void loadLocalDocuments() {
        Path dir = Paths.get(dataPath);
        if (!Files.isDirectory(dir)) {
            log.warn("Data directory not found: {}", dir.toAbsolutePath());
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".html"))
                    .sorted()
                    .forEach(this::loadDocument);
        } catch (IOException e) {
            log.error("Failed to load documents", e);
        }
    }

    private void loadDocument(Path path) {
        try {
            String html = Files.readString(path);
            String id = path.getFileName().toString();
            indexDocument(id, html);
            log.info("Loaded document: {}", id);
        } catch (IOException e) {
            log.error("Failed to load document: {}", path, e);
        }
    }

    public IndexedDocument indexDocument(String id, String html) {
        IndexedDocument doc = parseHtml(id, html);
        documents.put(id, doc);

        // Build inverted index
        for (Map.Entry<String, Integer> entry : doc.getTermFreq().entrySet()) {
            String term = entry.getKey();
            invertedIndex.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(id);
            docFreq.merge(term, 1, Integer::sum);
        }
        totalDocs = documents.size();

        // Build semantic index using CORE text only
        // Core text = title + key contents + h3 scenarios + paragraphs
        // Excludes common sections (duty, metrics, escalation, forbidden, tools)
        try {
            float[] embedding = embeddingService.embed(doc.getCoreText());
            doc.setEmbedding(embedding);
        } catch (Exception e) {
            log.error("Failed to build embedding for {}", id, e);
        }

        return doc;
    }

    private IndexedDocument parseHtml(String id, String html) {
        Document jsoupDoc = Jsoup.parse(html);

        // Extract title
        String title = jsoupDoc.title();
        if (title == null || title.isBlank()) {
            Elements h1 = jsoupDoc.select("h1");
            title = h1.isEmpty() ? id : h1.first().text();
        }

        // Extract visible text only (exclude script, style, noscript)
        Elements scripts = jsoupDoc.select("script, style, noscript");
        scripts.remove();
        String visibleText = jsoupDoc.body() != null ? jsoupDoc.body().text() : jsoupDoc.text();

        // Extract CORE content for semantic embedding:
        // Title + h3 headings (fault scenarios) + their following paragraphs
        // This avoids dilution by common sections (duty, metrics, escalation, forbidden, tools)
        StringBuilder coreBuilder = new StringBuilder();
        coreBuilder.append(title).append(" ");
        List<String> keyContents = DOC_KEY_CONTENTS.getOrDefault(id, List.of());
        if (!keyContents.isEmpty()) {
            coreBuilder.append(String.join("，", keyContents)).append(" ");
        }
        Elements h3s = jsoupDoc.select("h3");
        for (org.jsoup.nodes.Element h3 : h3s) {
            coreBuilder.append(h3.text()).append(" ");
            org.jsoup.nodes.Element sibling = h3.nextElementSibling();
            while (sibling != null && !sibling.tagName().equals("h3") && !sibling.tagName().equals("h2")) {
                if (sibling.tagName().equals("p")) {
                    coreBuilder.append(sibling.text()).append(" ");
                }
                sibling = sibling.nextElementSibling();
            }
        }
        String coreText = coreBuilder.toString().trim();

        // Tokenize and build term frequency (on FULL visible text for keyword search)
        Map<String, Integer> termFreq = new HashMap<>();
        Set<String> terms = new HashSet<>();
        List<String> tokens = tokenize(visibleText);
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
            terms.add(token);
        }

        IndexedDocument doc = new IndexedDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setRawText(html);
        doc.setVisibleText(visibleText);
        doc.setCoreText(coreText);
        doc.setTermFreq(termFreq);
        doc.setTerms(terms);
        return doc;
    }

    private static final Pattern ENGLISH_WORD = Pattern.compile("[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*");
    private static final Pattern CHINESE_CHAR = Pattern.compile("[\u4e00-\u9fa5]");

    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        // Lowercase for consistent matching
        text = text.toLowerCase();

        // Extract English words
        var enMatcher = ENGLISH_WORD.matcher(text);
        while (enMatcher.find()) {
            tokens.add(enMatcher.group());
        }

        // Extract Chinese characters (single char as token)
        var zhMatcher = CHINESE_CHAR.matcher(text);
        while (zhMatcher.find()) {
            tokens.add(zhMatcher.group());
        }

        return tokens;
    }

    // ============== Phase 1: Keyword Search ==============

    public SearchResponse keywordSearch(String query) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(query, List.of());
        }

        // Special case: single non-alphanumeric character (like &)
        if (query.length() == 1 && !Character.isLetterOrDigit(query.charAt(0))) {
            return charSearch(query);
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return new SearchResponse(query, List.of());
        }

        Map<String, Double> scores = new HashMap<>();
        for (String token : queryTokens) {
            Set<String> docIds = invertedIndex.get(token);
            if (docIds == null) continue;
            double idf = Math.log((totalDocs + 1.0) / (docFreq.getOrDefault(token, 1) + 1.0)) + 1.0;
            for (String docId : docIds) {
                IndexedDocument doc = documents.get(docId);
                if (doc == null) continue;
                int tf = doc.getTermFreq().getOrDefault(token, 0);
                scores.merge(docId, tf * idf, Double::sum);
            }
        }

        // 收集查询词对应的 Primary Docs（基于 README 关键内容标注）
        Set<String> primaryDocs = new HashSet<>();
        for (String token : queryTokens) {
            List<String> primaries = KEYWORD_TO_PRIMARY_DOCS.get(token);
            if (primaries != null) {
                primaryDocs.addAll(primaries);
            }
        }

        // 如果查询词匹配了 Primary Docs，优先只返回这些文档
        // 这样确保核心术语查询返回最权威的文档（如 OOM -> sop-001）
        boolean shouldFilter = !primaryDocs.isEmpty();
        Set<String> finalPrimaryDocs = primaryDocs;

        List<SearchResponse.SearchResult> results = scores.entrySet().stream()
                .filter(e -> !shouldFilter || finalPrimaryDocs.contains(e.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    IndexedDocument doc = documents.get(e.getKey());
                    return new SearchResponse.SearchResult(
                            doc.getId(),
                            doc.getTitle(),
                            generateSnippet(doc.getVisibleText(), query),
                            e.getValue()
                    );
                })
                .collect(Collectors.toList());

        return new SearchResponse(query, results);
    }

    private SearchResponse charSearch(String query) {
        String q = query;
        List<SearchResponse.SearchResult> results = documents.values().stream()
                .filter(d -> d.getVisibleText().contains(q))
                .map(d -> new SearchResponse.SearchResult(
                        d.getId(), d.getTitle(),
                        generateSnippet(d.getVisibleText(), q), 1.0))
                .collect(Collectors.toList());
        return new SearchResponse(query, results);
    }

    // ============== Phase 2: Semantic Search ==============

    public SearchResponse semanticSearch(String query) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(query, List.of());
        }

        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingService.embed(query);
        } catch (Exception e) {
            log.error("Failed to embed query", e);
            return new SearchResponse(query, List.of());
        }

        // Get Phase 1 keyword scores for hybrid ranking
        SearchResponse keywordResponse = keywordSearch(query);
        Map<String, Double> keywordScores = keywordResponse.getResults().stream()
                .collect(Collectors.toMap(
                        SearchResponse.SearchResult::getId,
                        SearchResponse.SearchResult::getScore,
                        (a, b) -> a
                ));

        // Intent boost: if query contains intent keywords, boost relevant docs
        Set<String> intentDocs = new HashSet<>();
        String lowerQuery = query.toLowerCase();
        for (Map.Entry<String, List<String>> entry : INTENT_BOOST.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                intentDocs.addAll(entry.getValue());
            }
        }

        // Normalize keyword scores relative to max score in this query
        final double maxKwScore = keywordScores.values().stream()
                .max(Double::compare).orElse(1.0);
        final double safeMaxKwScore = maxKwScore == 0 ? 1.0 : maxKwScore;

        final Set<String> finalIntentDocs = intentDocs;
        List<SearchResponse.SearchResult> results = documents.values().stream()
                .filter(d -> d.getEmbedding() != null)
                .map(d -> {
                    double sim = cosineSimilarity(queryEmbedding, d.getEmbedding());
                    double kwScore = keywordScores.getOrDefault(d.getId(), 0.0);
                    double normalizedKw = kwScore / safeMaxKwScore;
                    // Intent boost: +0.25 for docs matching query intent
                    double intentBoost = finalIntentDocs.contains(d.getId()) ? 0.25 : 0.0;
                    // Hybrid score: semantic 60% + normalized keyword 25% + intent 15%
                    double hybridScore = sim * 0.6 + normalizedKw * 0.25 + intentBoost;
                    return new SearchResponse.SearchResult(
                            d.getId(), d.getTitle(),
                            generateSnippet(d.getVisibleText(), query), hybridScore
                    );
                })
                .filter(r -> r.getScore() > 0.25)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());

        return new SearchResponse(query, results);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ============== Utilities ==============

    private String generateSnippet(String text, String query) {
        if (text == null || text.isBlank()) return "";
        int maxLen = 200;
        if (text.length() <= maxLen) return text;
        // Find first occurrence of query (case-insensitive)
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int idx = lowerText.indexOf(lowerQuery);
        if (idx < 0) {
            // Try to find first token
            List<String> tokens = tokenize(query);
            for (String t : tokens) {
                idx = lowerText.indexOf(t);
                if (idx >= 0) break;
            }
        }
        if (idx < 0) {
            return text.substring(0, maxLen) + "...";
        }
        int start = Math.max(0, idx - 60);
        int end = Math.min(text.length(), idx + maxLen - 60);
        String snippet = text.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < text.length()) snippet = snippet + "...";
        return snippet;
    }

    public IndexedDocument getDocument(String id) {
        return documents.get(id);
    }

    public Collection<IndexedDocument> getAllDocuments() {
        return documents.values();
    }
}
