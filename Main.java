package cleaner;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            Path configPath = Paths.get("out", "config.properties");
            Properties config = readConfig(configPath);
            String[][] records = new String[0][];

            try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
                while (true) {
                    showMenu();
                    if (!scanner.hasNextLine()) {
                        System.out.println("输入结束，程序退出。");
                        break;
                    }
                    String choice = scanner.nextLine().trim();

                    if ("1".equals(choice)) {
                        try {
                            Path input = configPath.getParent()
                                    .resolve(readRequired(config, "input.path"));
                            Path output = configPath.getParent()
                                    .resolve(readRequired(config, "output.dir")).resolve("cleaned.csv");
                            if (sameFile(input, output) || sameFile(configPath, output)) {
                                throw new IOException("输出文件不能覆盖原始 CSV 或配置文件。");
                            }

                            // 导入成功才替换当前账单；失败时仍保留原来的数组。
                            records = CsvCleaner.importCsv(input, output);
                            Ledger.showSummary(records);
                        } catch (IOException | IllegalArgumentException ex) {
                            System.out.println("导入失败，当前账单未替换：" + ex.getMessage());
                        }
                    } else if ("2".equals(choice)) {
                        Ledger.showSummary(records);
                    } else if ("3".equals(choice)) {
                        showAbout(config);
                    } else if ("4".equals(choice)) {
                        Ledger.browse(records, scanner);
                    } else if ("0".equals(choice)) {
                        System.out.println("程序已退出。");
                        break;
                    } else {
                        System.out.println("菜单输入无效，请输入 0、1、2、3 或 4。");
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("启动失败：" + ex.getMessage());
        }
    }

    private static void showMenu() {
        System.out.println();
        System.out.println("===== 个人记账本（控制台版） =====");
        System.out.println("1. 导入 CSV");
        System.out.println("2. 收支总览");
        System.out.println("3. 关于");
        System.out.println("4. 按日期浏览");
        System.out.println("0. 退出");
        System.out.print("请输入菜单编号：");
    }

    private static void showAbout(Properties config) {
        System.out.println("程序名称：个人记账本（控制台版）");
        System.out.println("学号：" + config.getProperty("student.id", ""));
        System.out.println("本人水印：" + config.getProperty("student.watermark", ""));
        System.out.println("当前进度：第 3 阶段（增加日期排序和分页浏览）");
    }

    private static Properties readConfig(Path configPath) throws IOException {
        Properties config = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    private static String readRequired(Properties config, String key) throws IOException {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("配置缺少必填路径：" + key);
        }
        return value.trim();
    }

    private static boolean sameFile(Path first, Path second) throws IOException {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())) {
            return true;
        }
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }
}
