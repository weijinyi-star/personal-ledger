package cleaner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public class CsvCleaner {
    private static final String HEADER =
            "id,date,account,type,category,amount,note,recurring";

    public static String[][] importCsv(Path input, Path output) throws IOException {
        int total = countRows(input);
        String[][] records = new String[total][];
        String[] errors = new String[total];
        int valid = 0;
        int discarded = 0;
        int processed = 0;

        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            checkHeader(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                if (processed >= total) {
                    throw new IOException("处理期间输入文件发生变化，请重新导入。");
                }
                processed++;
                int lineNumber = processed + 1;
                String[] fields = line.split(",", -1);

                String error = checkFields(fields);
                if (error == null && isDuplicate(fields, records, valid)) {
                    error = "八字段完全重复，保留第一次出现的记录";
                }

                if (error != null) {
                    errors[discarded] = "第 " + lineNumber + " 行：" + error;
                    discarded++;
                    continue;
                }
                records[valid] = fields;
                valid++;
            }
        }
        if (processed != total) {
            throw new IOException("处理期间输入文件发生变化，请重新导入。");
        }

        // 只把有效账单交给菜单，返回的数组里没有多余的空位置。
        String[][] cleaned = new String[valid][];
        for (int i = 0; i < valid; i++) {
            cleaned[i] = records[i];
        }

        saveCleaned(output, cleaned);
        System.out.println("总数：" + total + "，有效数：" + valid + "，丢弃数：" + discarded);
        for (int i = 0; i < discarded; i++) {
            System.out.println(errors[i]);
        }
        System.out.println("有效数据已保存到：" + output);
        return cleaned;
    }

    private static void checkHeader(String header) throws IOException {
        if (!HEADER.equals(header)) {
            throw new IOException("表头不正确；请使用 UTF-8 无 BOM，表头必须为：" + HEADER);
        }
    }

    private static int countRows(Path input) throws IOException {
        int total = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            checkHeader(reader.readLine());
            while (reader.readLine() != null) {
                total++;
            }
        }
        return total;
    }

    private static String checkFields(String[] fields) {
        if (fields.length != 8) {
            return "列数错误：应为 8 列，实际为 " + fields.length + " 列";
        }
        if (!fields[5].matches("[0-9]+(\\.[0-9]{1,2})?")) {
            return "金额错误：必须为非负数字且最多两位小数，实际为：" + fields[5];
        }
        if (parseDate(fields[1]) == null) {
            return "日期错误：必须是真实日期并符合三种格式，实际为：" + fields[1];
        }
        if (!"收入".equals(fields[3]) && !"支出".equals(fields[3])) {
            return "类型错误：只能为收入或支出";
        }
        if (!fields[0].matches("[0-9]+")) {
            return "id 错误：必须为非空数字";
        }

        String account = fields[2];
        if (!"微信".equals(account) && !"支付宝".equals(account)
                && !"校园卡".equals(account) && !"银行卡".equals(account)) {
            return "账户错误：只能为微信、支付宝、校园卡、银行卡";
        }
        if (!fields[4].matches("\\p{IsHan}+")) {
            return "类别错误：必须非空且只能包含汉字";
        }
        if (!"Y".equals(fields[7]) && !"N".equals(fields[7])) {
            return "标记错误：recurring 只能为大写 Y 或 N";
        }
        return null;
    }

    public static LocalDate parseDate(String text) {
        String format;
        if (text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            format = "uuuu-MM-dd";
        } else if (text.matches("[0-9]{4}/[0-9]{1,2}/[0-9]{1,2}")) {
            format = "uuuu/M/d";
        } else if (text.matches("[0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日")) {
            format = "uuuu年M月d日";
        } else {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
                    .withResolverStyle(ResolverStyle.STRICT);
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean isDuplicate(String[] fields, String[][] records, int valid) {
        for (int i = 0; i < valid; i++) {
            boolean same = true;
            for (int j = 0; j < 8; j++) {
                if (!fields[j].equals(records[i][j])) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return true;
            }
        }
        return false;
    }

    private static void saveCleaned(Path output, String[][] records) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (int i = 0; i < records.length; i++) {
                writer.write(String.join(",", records[i]));
                writer.newLine();
            }
        }
    }
}
