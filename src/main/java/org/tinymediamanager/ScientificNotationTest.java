package org.tinymediamanager;

import org.tinymediamanager.scraper.util.MetadataUtil;

/**
 * 简单测试程序验证科学计数法解析修复
 */
public class ScientificNotationTest {
    
    public static void main(String[] args) {
        System.out.println("=== 科学计数法解析测试 ===");
        
        // 测试用例
        String[] testCases = {
            "2019",      // 标准格式
            "2019E",     // 问题格式 - 这是导致错误的原因
            "2E3",       // 科学计数法
            "1.5E3",     // 小数科学计数法
            "1E6",       // 大数科学计数法
            "-1.5E3",    // 负数科学计数法
            "2,019",     // 带分隔符
            "2.019",     // 欧洲格式
            "0",         // 零
            "-123"       // 负数
        };
        
        int[] expectedResults = {
            2019,        // "2019"
            2019,        // "2019E" - 应该解析为2019
            2000,        // "2E3"
            1500,        // "1.5E3"
            1000000,     // "1E6"
            -1500,       // "-1.5E3"
            2019,        // "2,019"
            2019,        // "2.019"
            0,           // "0"
            -123         // "-123"
        };
        
        boolean allPassed = true;
        
        for (int i = 0; i < testCases.length; i++) {
            String testCase = testCases[i];
            int expected = expectedResults[i];
            
            try {
                int result = MetadataUtil.parseInt(testCase);
                if (result == expected) {
                    System.out.println("✅ PASS: '" + testCase + "' -> " + result);
                } else {
                    System.out.println("❌ FAIL: '" + testCase + "' -> " + result + " (expected: " + expected + ")");
                    allPassed = false;
                }
            } catch (Exception e) {
                System.out.println("❌ ERROR: '" + testCase + "' -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
                allPassed = false;
            }
        }
        
        System.out.println("\n=== 测试结果 ===");
        if (allPassed) {
            System.out.println("🎉 所有测试通过！科学计数法解析修复成功！");
        } else {
            System.out.println("❌ 部分测试失败，需要进一步调试。");
        }
        
        // 特别测试问题案例
        System.out.println("\n=== 特别测试：问题案例 ===");
        try {
            int result = MetadataUtil.parseInt("2019E");
            System.out.println("🎯 关键测试：'2019E' -> " + result);
            System.out.println("   这个值之前会导致 NumberFormatException，现在应该正常解析！");
        } catch (Exception e) {
            System.out.println("❌ 关键测试失败：'2019E' -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
