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
    public static final int ID = 0;
    public static final int DATE = 1;
    public static final int ACCOUNT = 2;
    public static final int TYPE = 3;
    public static final int CATEGORY = 4;
    public static final int AMOUNT = 5;
    public static final int NOTE = 6;
    public static final int RECURRING = 7;
    public static final int FIELD_COUNT = 8;

    private static final String HEADER =
            "id,date,account,type,category,amount,note,recurring";

    public static String[][] importCsv(Path input, Path output, String[][] current,
            String[][] saved) throws IOException {
        String[][] cleaned = readCsv(input, current, false);
        String[][] merged = mergeRecords(cleaned, saved);
        saveRecords(output, merged);
        System.out.println("合并后账单数：" + merged.length);
        System.out.println("清洗后的合并数据已保存到：" + output);
        return merged;
    }

    public static String[][] mergeRecords(String[][] first, String[][] second) {
        String[][] merged = new String[first.length + second.length][];
        int count = first.length;
        for (int i = 0; i < first.length; i++) {
            merged[i] = first[i];
        }
        for (int i = 0; i < second.length; i++) {
            if (!isDuplicate(second[i], merged, count)) {
                merged[count] = second[i];
                count++;
            }
        }
        String[][] result = new String[count][];
        for (int i = 0; i < count; i++) {
            result[i] = merged[i];
        }
        return result;
    }

    public static String[][] loadSaved(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new String[0][];
        }
        String[][] records = readCsv(path, new String[0][], true);
        return records;
    }

    private static String[][] readCsv(Path input, String[][] current, boolean strict) throws IOException {
        int total = countRows(input);
        String[][] records = new String[current.length + total][];
        for (int i = 0; i < current.length; i++) {
            records[i] = current[i];
        }
        String[] errors = new String[total];
        int valid = current.length;
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
                    error = "八字段完全重复，保留已有记录或文件中首次出现的记录";
                }
                if (error != null) {
                    if (strict) {
                        throw new IOException("本地账单第 " + lineNumber + " 行：" + error
                                + "。请修复该文件后重试，当前不会覆盖它。");
                    }
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
        String[][] cleaned = new String[valid][];
        for (int i = 0; i < valid; i++) {
            cleaned[i] = records[i];
        }
        if (!strict) {
            System.out.println("本次导入总数：" + total + "，新增有效数：" + (valid - current.length)
                    + "，丢弃数：" + discarded);
            for (int i = 0; i < discarded; i++) {
                System.out.println(errors[i]);
            }
        }
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
        if (fields.length != FIELD_COUNT) {
            return "列数错误：应为 " + FIELD_COUNT + " 列，实际为 " + fields.length + " 列";
        }
        int[] order = {AMOUNT, DATE, TYPE, ID, ACCOUNT, CATEGORY, RECURRING, NOTE};
        for (int i = 0; i < order.length; i++) {
            String error = checkField(order[i], fields[order[i]]);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    public static String checkField(int index, String value) {
        if (index == ID && !value.matches("[0-9]+")) {
            return "id 错误：必须为非空数字";
        }
        if (index == DATE && parseDate(value) == null) {
            return "日期错误：必须是真实日期并符合三种格式，实际为：" + value;
        }
        if (index == ACCOUNT && !"微信".equals(value) && !"支付宝".equals(value)
                && !"校园卡".equals(value) && !"银行卡".equals(value)) {
            return "账户错误：只能为微信、支付宝、校园卡、银行卡";
        }
        if (index == TYPE && !"收入".equals(value) && !"支出".equals(value)) {
            return "类型错误：只能为收入或支出";
        }
        if (index == CATEGORY && !value.matches("\\p{IsHan}+")) {
            return "类别错误：必须非空且只能包含汉字";
        }
        if (index == AMOUNT && !value.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            return "金额错误：必须为非负数字且最多两位小数，实际为：" + value;
        }
        if (index == NOTE && value.contains(",")) {
            return "备注不能含半角逗号，否则保存为简单 CSV 后会多出一列";
        }
        if (index == RECURRING && !"Y".equals(value) && !"N".equals(value)) {
            return "标记错误：是否固定支出只能为大写 Y 或 N";
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

    public static boolean isDuplicate(String[] fields, String[][] records, int valid) {
        for (int i = 0; i < valid; i++) {
            boolean same = true;
            for (int j = 0; j < FIELD_COUNT; j++) {
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

    public static void saveRecords(Path output, String[][] records) throws IOException {
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

    public static void exportReport(Path output, String[][] report) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("月份,类别,支出金额,预算,是否超支");
            writer.newLine();
            for (int i = 0; i < report.length; i++) {
                writer.write(String.join(",", report[i]));
                writer.newLine();
            }
        }
    }
}
