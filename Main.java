package cleaner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    private static String weatherCity = "";
    private static String weatherMessage = "";

    public static void main(String[] args) {
        try {
            if (System.console() == null) {
                System.setOut(new PrintStream(System.out, true, "UTF-8"));
            }
            Path configPath = Paths.get("out", "config.properties");
            Properties config = readConfig(configPath);
            Path input = configPath.getParent().resolve(readRequired(config, "input.path"));
            Path output = configPath.getParent().resolve(readRequired(config, "output.dir"))
                    .resolve("cleaned.csv");
            Path storage = configPath.getParent().resolve(readRequired(config, "storage.path"));
            Path reportPath = configPath.getParent().resolve(readRequired(config, "report.path"));
            checkPaths(new Path[] {configPath, input, output, storage, reportPath});
            Ledger.checkBudgetConfig(config);
            String[][] records = new String[0][];
            boolean savedLoaded = false;

            try (Scanner scanner = createScanner()) {
                while (true) {
                    showMenu();
                    if (!scanner.hasNextLine()) {
                        System.out.println("输入结束，尝试保存账单。");
                        saveBeforeExit(storage, records, savedLoaded);
                        break;
                    }
                    String choice = scanner.nextLine().trim();
                    if ("1".equals(choice)) {
                        try {
                            String[][] saved = new String[0][];
                            if (!savedLoaded) {
                                saved = CsvCleaner.loadSaved(storage);
                            }
                            records = CsvCleaner.importCsv(input, output, records, saved);
                            savedLoaded = true;
                            Ledger.showSummary(records);
                            Ledger.showBudgetWarnings(records, config);
                        } catch (IOException | IllegalArgumentException ex) {
                            System.out.println("导入失败，当前账单未替换：" + ex.getMessage());
                        }
                    } else if ("2".equals(choice)) {
                        Ledger.showSummary(records);
                    } else if ("3".equals(choice)) {
                        showAbout(config);
                    } else if ("4".equals(choice)) {
                        Ledger.browse(records, scanner);
                    } else if ("5".equals(choice)) {
                        int previousCount = records.length;
                        records = Ledger.addManually(records, scanner);
                        Ledger.showMonthlyReport(records, config);
                        if (records.length > previousCount) {
                            showWeather(config, false);
                        }
                    } else if ("6".equals(choice)) {
                        config = changeBudget(records, config, configPath, scanner);
                        Ledger.showMonthlyReport(records, config);
                    } else if ("7".equals(choice)) {
                        Ledger.showMonthlyReport(records, config);
                    } else if ("8".equals(choice)) {
                        try {
                            String[][] report = Ledger.buildMonthlyReport(records, config);
                            CsvCleaner.exportReport(reportPath, report);
                            System.out.println("月报已导出：" + reportPath + "，共 " + report.length + " 行");
                        } catch (IOException ex) {
                            System.out.println("月报导出失败：" + ex.getMessage());
                        }
                    } else if ("9".equals(choice)) {
                        showWeather(config, true);
                    } else if ("0".equals(choice)) {
                        if (saveBeforeExit(storage, records, savedLoaded)) {
                            break;
                        }
                    } else {
                        System.out.println("菜单输入无效，请输入 0～9 中的一个编号。");
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("启动失败：" + ex.getMessage());
        }
    }

    private static Scanner createScanner() {
        if (System.console() != null) {
            return new Scanner(System.console().reader());
        }
        return new Scanner(System.in, "UTF-8");
    }

    private static void showMenu() {
        System.out.println();
        System.out.println("===== 个人记账本（控制台版） =====");
        System.out.println("1. 导入 CSV（与当前账单合并）");
        System.out.println("2. 收支总览");
        System.out.println("3. 关于");
        System.out.println("4. 按日期浏览");
        System.out.println("5. 手动记账");
        System.out.println("6. 类别月预算管理（查看、增加、修改、删除）");
        System.out.println("7. 月度分类支出与超支提醒");
        System.out.println("8. 导出月度报表 CSV");
        System.out.println("9. 查询或刷新天气");
        System.out.println("0. 保存并退出");
        System.out.print("请输入菜单编号：");
    }

    private static void showAbout(Properties config) {
        System.out.println("程序名称：个人记账本（控制台版）");
        System.out.println("学号：" + config.getProperty("student.id", ""));
        System.out.println("本人水印：" + config.getProperty("student.watermark", ""));
    }

    private static void showWeather(Properties config, boolean refresh) {
        String city = config.getProperty("weather.city", "").trim();
        if (city.isEmpty()) {
            System.out.println("未设置天气城市，请在配置文件中填写 weather.city；仍可继续记账。");
            return;
        }
        if (refresh || !city.equals(weatherCity) || weatherMessage.isEmpty()) {
            System.out.println("正在查询" + city + "天气，请稍候……");
            weatherCity = city;
            try {
                weatherMessage = WeatherKit.now(city);
            } catch (WeatherKit.ApiException ex) {
                weatherMessage = "暂时无法获取天气：" + ex.getMessage()
                        + "。仍可继续记账，稍后输入 9 重试。";
            }
        } else {
            System.out.println("以下天气信息来自本次运行的上次查询，输入 9 可刷新。");
        }
        System.out.println("天气信息：" + weatherMessage);
    }

    private static boolean saveBeforeExit(Path storage, String[][] records, boolean savedLoaded) {
        if (!savedLoaded && records.length == 0) {
            System.out.println("程序已退出。");
            return true;
        }
        try {
            if (!savedLoaded) {
                String[][] saved = CsvCleaner.loadSaved(storage);
                records = CsvCleaner.mergeRecords(saved, records);
            }
            CsvCleaner.saveRecords(storage, records);
            System.out.println("已保存 " + records.length + " 条账单到：" + storage);
            System.out.println("程序已退出。");
            return true;
        } catch (IOException ex) {
            System.out.println("保存失败：" + ex.getMessage());
            System.out.println("账单尚未成功保存；若输入仍可用，可修复路径后选择 0 重试。");
            return false;
        }
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

    private static void checkPaths(Path[] paths) throws IOException {
        for (int i = 0; i < paths.length; i++) {
            for (int j = i + 1; j < paths.length; j++) {
                if (sameFile(paths[i], paths[j])) {
                    throw new IOException("配置、原始 CSV、清洗结果、保存文件和月报必须使用不同路径："
                            + paths[i] + " 与 " + paths[j]);
                }
            }
        }
    }

    private static boolean sameFile(Path first, Path second) throws IOException {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())) {
            return true;
        }
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private static Properties changeBudget(String[][] records, Properties config,
            Path configPath, Scanner scanner) {
        Ledger.showBudgets(records, config);
        System.out.println("输入要管理的类别；输入 q 或 :q 返回。");
        String category;
        while (true) {
            System.out.print("类别：");
            if (!scanner.hasNextLine()) {
                return config;
            }
            category = scanner.nextLine();
            String command = category.trim();
            if ("q".equalsIgnoreCase(command) || ":q".equalsIgnoreCase(command)) {
                System.out.println("已取消预算修改，返回菜单。");
                return config;
            }
            if (CsvCleaner.checkField(CsvCleaner.CATEGORY, category) == null) {
                break;
            }
            System.out.println("类别必须是纯汉字，请重新输入。");
        }

        if (!Ledger.hasBudget(config, category)) {
            System.out.println(category + "没有设置预算。");
            while (true) {
                System.out.print("是否增加该类别预算？（是/否，q 或 :q 返回）：");
                if (!scanner.hasNextLine()) {
                    return config;
                }
                String answer = scanner.nextLine().trim();
                if ("否".equals(answer) || "q".equalsIgnoreCase(answer)
                        || ":q".equalsIgnoreCase(answer)) {
                    System.out.println("未增加该类别预算，返回菜单。");
                    return config;
                }
                if ("是".equals(answer)) {
                    break;
                }
                System.out.println("请输入“是”或“否”，也可输入 q 或 :q 返回。");
            }
        } else {
            System.out.println(category + "当前月预算："
                    + Ledger.getBudget(config, category).toPlainString() + " 元。");
            while (true) {
                System.out.println("1. 修改预算  2. 删除预算  0. 返回菜单");
                System.out.print("请选择操作（q 或 :q 也可返回）：");
                if (!scanner.hasNextLine()) {
                    return config;
                }
                String choice = scanner.nextLine().trim();
                if ("0".equals(choice) || "q".equalsIgnoreCase(choice)
                        || ":q".equalsIgnoreCase(choice)) {
                    System.out.println("已返回菜单，预算未修改。");
                    return config;
                }
                if ("1".equals(choice)) {
                    break;
                }
                if ("2".equals(choice)) {
                    return deleteBudget(category, config, configPath);
                }
                System.out.println("操作无效，请输入 0、1 或 2。");
            }
        }

        String amount;
        while (true) {
            System.out.print("月预算（非负，最多两位小数；q 或 :q 取消）：");
            if (!scanner.hasNextLine()) {
                return config;
            }
            amount = scanner.nextLine();
            String command = amount.trim();
            if ("q".equalsIgnoreCase(command) || ":q".equalsIgnoreCase(command)) {
                System.out.println("已取消预算修改，返回菜单。");
                return config;
            }
            String error = CsvCleaner.checkField(CsvCleaner.AMOUNT, amount);
            if (error == null) {
                break;
            }
            System.out.println(error + "，请重新输入。");
        }

        Properties changed = new Properties();
        changed.putAll(config);
        String[] categories = Ledger.getBudgetCategories(records, config);
        String[] names = new String[categories.length + 1];
        int count = 0;
        boolean found = false;
        for (int i = 0; i < categories.length; i++) {
            names[count] = categories[i];
            count++;
            if (category.equals(categories[i])) {
                found = true;
            }
        }
        if (!found) {
            names[count] = category;
            count++;
        }
        String list = "";
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                list += ",";
            }
            list += names[i];
        }
        changed.setProperty("budget.categories", list);
        changed.setProperty("budget." + category, amount);
        if (saveBudgetConfig(changed, configPath)) {
            System.out.println("预算已保存到配置文件。");
            return changed;
        }
        return config;
    }

    private static Properties deleteBudget(String category, Properties config, Path configPath) {
        Properties changed = new Properties();
        changed.putAll(config);
        changed.remove("budget." + category);

        String[] categories = config.getProperty("budget.categories", "").split(",", -1);
        String list = "";
        for (int i = 0; i < categories.length; i++) {
            if (!categories[i].isEmpty() && !category.equals(categories[i])) {
                if (!list.isEmpty()) {
                    list += ",";
                }
                list += categories[i];
            }
        }
        changed.setProperty("budget.categories", list);
        if (saveBudgetConfig(changed, configPath)) {
            System.out.println(category + "预算已删除，返回菜单。");
            return changed;
        }
        return config;
    }

    private static boolean saveBudgetConfig(Properties config, Path configPath) {
        try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            config.store(writer, "UTF-8 ledger configuration");
            return true;
        } catch (IOException ex) {
            System.out.println("预算保存失败，内存预算未修改：" + ex.getMessage());
            return false;
        }
    }
}
