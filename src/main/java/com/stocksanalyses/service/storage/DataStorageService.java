package com.stocksanalyses.service.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地数据存储服务
 */
@Service
public class DataStorageService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String basePath = "data";
    private static final Schema CANDLE_SCHEMA = new Schema.Parser().parse("{\n" +
            "  \"type\": \"record\",\n" +
            "  \"name\": \"CandleData\",\n" +
            "  \"fields\": [\n" +
            "    {\"name\":\"symbol\",\"type\":[\"null\",\"string\"],\"default\":null},\n" +
            "    {\"name\":\"market\",\"type\":[\"null\",\"string\"],\"default\":null},\n" +
            "    {\"name\":\"timestamp\",\"type\":\"long\"},\n" +
            "    {\"name\":\"open\",\"type\":\"double\"},\n" +
            "    {\"name\":\"high\",\"type\":\"double\"},\n" +
            "    {\"name\":\"low\",\"type\":\"double\"},\n" +
            "    {\"name\":\"close\",\"type\":\"double\"},\n" +
            "    {\"name\":\"volume\",\"type\":\"long\"},\n" +
            "    {\"name\":\"amount\",\"type\":\"double\"},\n" +
            "    {\"name\":\"count\",\"type\":\"int\"}\n" +
            "  ]\n" +
            "}");
    
    // 市场类型
    public enum Market {
        CN("cn", "A股"),
        HK("hk", "港股"), 
        US("us", "美股");
        
        private final String code;
        private final String name;
        
        Market(String code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public String getCode() { return code; }
        public String getName() { return name; }
    }
    
    // K线数据类型
    public static class CandleData {
        public String symbol;
        public String market;
        public long timestamp;
        public double open;
        public double high;
        public double low;
        public double close;
        public long volume;
        public double amount;
        public int count;
        
        public CandleData() {}
        
        public CandleData(String symbol, String market, long timestamp, 
                         double open, double high, double low, double close, 
                         long volume, double amount, int count) {
            this.symbol = symbol;
            this.market = market;
            this.timestamp = timestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.amount = amount;
            this.count = count;
        }
    }
    
    // 新闻数据类型
    public static class NewsData {
        public String id;
        public String title;
        public String content;
        public String summary;
        public String source;
        public String url;
        public long timestamp;
        public String date;
        public List<String> symbols;
        public Map<String, Object> sentiment;
        public Map<String, Object> metadata;
        
        public NewsData() {}
    }
    
    // AI分析结果类型
    public static class AiAnalysisResult {
        public String id;
        public String type; // sentiment, finance, screener
        public String symbol;
        public String market;
        public long timestamp;
        public String date;
        public Map<String, Object> result;
        public Map<String, Object> metadata;
        
        public AiAnalysisResult() {}
    }
    
    /**
     * 存储K线数据（Parquet格式）
     */
    public void storeCandleData(String market, String symbol, List<CandleData> candles, boolean useParquet) {
        try {
            java.nio.file.Path dir = Paths.get(basePath, "quotes", market);
            Files.createDirectories(dir);
            
            String filename = symbol + (useParquet ? ".parquet" : ".csv");
            java.nio.file.Path file = dir.resolve(filename);
            
            if (useParquet) {
                storeCandleDataAsParquet(file, candles);
            } else {
                storeCandleDataAsCsv(file, candles);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to store candle data", e);
        }
    }
    
    /**
     * 读取K线数据
     */
    public List<CandleData> loadCandleData(String market, String symbol, boolean preferParquet) {
        try {
            java.nio.file.Path dir = Paths.get(basePath, "quotes", market);
            
            // 优先读取Parquet，其次CSV
            java.nio.file.Path parquetFile = dir.resolve(symbol + ".parquet");
            java.nio.file.Path csvFile = dir.resolve(symbol + ".csv");
            
            if (preferParquet && Files.exists(parquetFile)) {
                return loadCandleDataFromParquet(parquetFile);
            } else if (Files.exists(csvFile)) {
                return loadCandleDataFromCsv(csvFile);
            } else if (Files.exists(parquetFile)) {
                return loadCandleDataFromParquet(parquetFile);
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load candle data", e);
        }
    }
    
    /**
     * 存储新闻数据
     */
    public void storeNewsData(String date, NewsData news) {
        try {
            Path dir = Paths.get(basePath, "news", date);
            Files.createDirectories(dir);
            
            String filename = news.id + ".json";
            Path file = dir.resolve(filename);
            
            String json = objectMapper.writeValueAsString(news);
            Files.writeString(file, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store news data", e);
        }
    }
    
    /**
     * 读取新闻数据
     */
    public List<NewsData> loadNewsData(String date) {
        try {
            Path dir = Paths.get(basePath, "news", date);
            if (!Files.exists(dir)) {
                return new ArrayList<>();
            }
            
            return Files.list(dir)
                .filter(path -> path.toString().endsWith(".json"))
                .map(this::loadNewsDataFromFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load news data", e);
        }
    }
    
    /**
     * 存储AI分析结果
     */
    public void storeAiAnalysisResult(String type, AiAnalysisResult result) {
        try {
            Path dir = Paths.get(basePath, "ai", type);
            Files.createDirectories(dir);
            
            String filename = result.id + ".json";
            Path file = dir.resolve(filename);
            
            String json = objectMapper.writeValueAsString(result);
            Files.writeString(file, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store AI analysis result", e);
        }
    }
    
    /**
     * 读取AI分析结果
     */
    public List<AiAnalysisResult> loadAiAnalysisResults(String type, String symbol, String date) {
        try {
            Path dir = Paths.get(basePath, "ai", type);
            if (!Files.exists(dir)) {
                return new ArrayList<>();
            }
            
            return Files.list(dir)
                .filter(path -> path.toString().endsWith(".json"))
                .map(this::loadAiAnalysisResultFromFile)
                .filter(Objects::nonNull)
                .filter(result -> symbol == null || symbol.equals(result.symbol))
                .filter(result -> date == null || date.equals(result.date))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load AI analysis results", e);
        }
    }
    
    /**
     * 列出可用的市场数据
     */
    public Map<String, List<String>> listAvailableData() {
        Map<String, List<String>> result = new HashMap<>();
        
        for (Market market : Market.values()) {
            Path marketDir = Paths.get(basePath, "quotes", market.getCode());
            if (Files.exists(marketDir)) {
                try {
                    List<String> symbols = Files.list(marketDir)
                        .map(path -> {
                            String filename = path.getFileName().toString();
                            return filename.substring(0, filename.lastIndexOf('.'));
                        })
                        .distinct()
                        .collect(Collectors.toList());
                    result.put(market.getCode(), symbols);
                } catch (IOException e) {
                    // 忽略读取错误
                }
            }
        }
        
        return result;
    }
    
    /**
     * 数据迁移：CSV到Parquet
     */
    public void migrateCsvToParquet(String market, String symbol) {
        try {
            java.nio.file.Path dir = Paths.get(basePath, "quotes", market);
            java.nio.file.Path csvFile = dir.resolve(symbol + ".csv");
            java.nio.file.Path parquetFile = dir.resolve(symbol + ".parquet");
            
            if (Files.exists(csvFile) && !Files.exists(parquetFile)) {
                List<CandleData> candles = loadCandleDataFromCsv(csvFile);
                storeCandleDataAsParquet(parquetFile, candles);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to migrate CSV to Parquet", e);
        }
    }
    
    // 私有方法：Parquet存储（Avro + Parquet）
    private void storeCandleDataAsParquet(java.nio.file.Path file, List<CandleData> candles) throws IOException {
        Files.createDirectories(file.getParent());
        Configuration conf = new Configuration();
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new Path(file.toString()))
                .withSchema(CANDLE_SCHEMA)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.GZIP)
                .withPageSize(1024 * 1024)
                .withRowGroupSize(128 * 1024 * 1024)
                .build()) {
            for (CandleData c : candles) {
                GenericRecord r = new GenericData.Record(CANDLE_SCHEMA);
                r.put("symbol", c.symbol);
                r.put("market", c.market);
                r.put("timestamp", c.timestamp);
                r.put("open", c.open);
                r.put("high", c.high);
                r.put("low", c.low);
                r.put("close", c.close);
                r.put("volume", c.volume);
                r.put("amount", c.amount);
                r.put("count", c.count);
                writer.write(r);
            }
        }
    }
    
    // 私有方法：CSV存储
    private void storeCandleDataAsCsv(java.nio.file.Path file, List<CandleData> candles) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("symbol,market,timestamp,open,high,low,close,volume,amount,count\n");
        
        for (CandleData candle : candles) {
            csv.append(String.format("%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%d,%.2f,%d\n",
                candle.symbol, candle.market, candle.timestamp,
                candle.open, candle.high, candle.low, candle.close,
                candle.volume, candle.amount, candle.count));
        }
        
        Files.writeString(file, csv.toString());
    }
    
    // 私有方法：从Parquet读取
    private List<CandleData> loadCandleDataFromParquet(java.nio.file.Path file) throws IOException {
        List<CandleData> out = new ArrayList<>();
        Configuration conf = new Configuration();
        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(file.toString()))
                .withConf(conf)
                .build()) {
            GenericRecord r;
            while ((r = reader.read()) != null) {
                CandleData c = new CandleData(
                    Optional.ofNullable((CharSequence) r.get("symbol")).map(Object::toString).orElse(null),
                    Optional.ofNullable((CharSequence) r.get("market")).map(Object::toString).orElse(null),
                    (Long) r.get("timestamp"),
                    (Double) r.get("open"),
                    (Double) r.get("high"),
                    (Double) r.get("low"),
                    (Double) r.get("close"),
                    (Long) r.get("volume"),
                    (Double) r.get("amount"),
                    (Integer) r.get("count")
                );
                out.add(c);
            }
        }
        return out;
    }
    
    // 私有方法：从CSV读取
    private List<CandleData> loadCandleDataFromCsv(java.nio.file.Path file) throws IOException {
        List<CandleData> candles = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        
        if (lines.size() <= 1) return candles;
        
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length >= 10) {
                CandleData candle = new CandleData(
                    parts[0], parts[1], Long.parseLong(parts[2]),
                    Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                    Double.parseDouble(parts[5]), Double.parseDouble(parts[6]),
                    Long.parseLong(parts[7]), Double.parseDouble(parts[8]),
                    Integer.parseInt(parts[9])
                );
                candles.add(candle);
            }
        }
        
        return candles;
    }
    
    // 私有方法：从文件读取新闻数据
    private NewsData loadNewsDataFromFile(Path file) {
        try {
            String json = Files.readString(file);
            return objectMapper.readValue(json, NewsData.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    // 私有方法：从文件读取AI分析结果
    private AiAnalysisResult loadAiAnalysisResultFromFile(Path file) {
        try {
            String json = Files.readString(file);
            return objectMapper.readValue(json, AiAnalysisResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
