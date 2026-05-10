package com.youranli.argo.service;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRegistryDemo {

    // 跟真 DatasetRegistry 里一样的 Pattern
    private static final Pattern PROF_FILE = Pattern.compile("(\\d+)_prof\\.nc$");
    
    // 跟真项目里一样的 Entry record
    public record Entry(String floatId, String profPath, String metaPath) {}
    
    public static void main(String[] args) {
        // 模拟 data/ 文件夹里的文件列表（不真的扫文件，直接用数组）
        String[] filenames = {
            "5906551_prof.nc",
            "5906551_meta.nc",
            "5906551_Sprof.nc",
            "5906552_prof.nc",
            "README.md",
            "random_file.txt",
            "1234567_prof.nc",
            "1234567_meta.nc"
        };
        
        // 注册表 Map
        Map<String, Entry> registry = new LinkedHashMap<>();
        
        // 扫描逻辑
        System.out.println("=== 扫描文件 ===");
        for (String filename : filenames) {
            Matcher m = PROF_FILE.matcher(filename);
            
            if (m.find()) {
                // 抠出 WMO 号
                String floatId = m.group(1);
                
                // 看看同目录有没有对应的 meta.nc
                String expectedMeta = floatId + "_meta.nc";
                String metaPath = null;
                for (String f : filenames) {
                    if (f.equals(expectedMeta)) {
                        metaPath = expectedMeta;
                        break;
                    }
                }
                
                Entry entry = new Entry(floatId, filename, metaPath);
                registry.put(floatId, entry);
                
                System.out.println("✓ 匹配: " + filename + " → floatId=" + floatId);
            } else {
                System.out.println("✗ 跳过: " + filename + " (不匹配 pattern)");
            }
        }
        
        // 输出注册表内容
        System.out.println("\n=== 注册表内容 ===");
        System.out.println("总数: " + registry.size());
        for (Entry e : registry.values()) {
            System.out.println(e);
        }
        
        // 按 floatId 查询
        System.out.println("\n=== 查询 ===");
        Entry e1 = registry.get("5906551");
        System.out.println("查 '5906551': " + e1);
        
        Entry e2 = registry.get("5906552");
        System.out.println("查 '5906552': " + e2);
        
        Entry e3 = registry.get("9999999");
        System.out.println("查 '9999999': " + e3);   // null（没找到）
    }
}
