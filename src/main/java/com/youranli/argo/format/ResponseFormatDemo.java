package com.youranli.argo.format;

public class ResponseFormatDemo {
    
    public static void main(String[] args) {
        // 例子 1：从字符串拿到 enum 实例
        ResponseFormat f1 = ResponseFormat.fromString("json");
        System.out.println("Input 'json'  → " + f1);
        
        ResponseFormat f2 = ResponseFormat.fromString("CSV");      // 大小写不敏感
        System.out.println("Input 'CSV'   → " + f2);
        
        ResponseFormat f3 = ResponseFormat.fromString("Json");
        System.out.println("Input 'Json'  → " + f3);
        
        // 例子 2：拿到 enum 后查 mimeType
        System.out.println("JSON 的 mime: " + f1.mimeType());
        System.out.println("CSV 的 mime:  " + f2.mimeType());
        
        // 例子 3：直接从枚举常量拿
        System.out.println("直接拿 JSON: " + ResponseFormat.JSON);
        System.out.println("JSON.mimeType: " + ResponseFormat.JSON.mimeType());
        
        // 例子 4：错误的输入会抛异常
        try {
            ResponseFormat f4 = ResponseFormat.fromString("xml");
        } catch (IllegalArgumentException e) {
            System.out.println("Input 'xml'   → 异常: " + e.getMessage());
        }
    }
}