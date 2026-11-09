package cleaner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Scanner;
import java.util.Properties;

public class Ledger {
    private static final int PAGE_SIZE = 10;
    private static final int MONEY_SCALE = 2;
    private static final int REPORT_MONTH = 0;
    private static final int REPORT_CATEGORY = 1;
    private static final int REPORT_EXPENSE = 2;
    private static final int REPORT_BUDGET = 3;
    private static final int REPORT_OVER = 4;

    public static void showSummary(String[][] records) {
        if (records.length == 0) {
            System.out.println("当前没有有效账单，请先导入包含有效记录的 CSV。");
            return;
        }

        BigDecimal income = new BigDecimal("0.00");
        BigDecimal expense = new BigDecimal("0.00");
        for (int i = 0; i < records.length; i++) {
            BigDecimal amount = new BigDecimal(records[i][CsvCleaner.AMOUNT]);
            if ("收入".equals(records[i][CsvCleaner.TYPE])) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
        }
        System.out.println("当前账单数：" + records.length);
        System.out.println("收入：" + income.toPlainString());
        System.out.println("支出：" + expense.toPlainString());
        System.out.println("结余：" + income.subtract(expense).toPlainString());
    }

    public static void browse(String[][] records, Scanner scanner) {
        if (records.length == 0) {
            System.out.println("当前没有有效账单，请先导入包含有效记录的 CSV。");
            return;
        }

        String[][] sorted = sortByDate(records);
        int page = 0;
        int totalPages = (sorted.length + PAGE_SIZE - 1) / PAGE_SIZE;

        while (true) {
            int start = page * PAGE_SIZE;
            int end = start + PAGE_SIZE;
            if (end > sorted.length) {
                end = sorted.length;
            }

            System.out.println();
            System.out.println("===== 日期升序浏览：第 " + (page + 1) + "/" + totalPages
                    + " 页，共 " + sorted.length + " 条 =====");
            System.out.println("id | 日期 | 账户 | 类型 | 类别 | 金额 | 备注 | 是否固定支出");
            for (int i = start; i < end; i++) {
                System.out.println(String.join(" | ", sorted[i]));
            }
            System.out.println("1. 上一页  2. 下一页  0. 返回菜单");
            System.out.print("请输入翻页编号：");
            if (!scanner.hasNextLine()) {
                return;
            }
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                if (page > 0) {
                    page--;
                } else {
                    System.out.println("已经是第一页。");
                }
            } else if ("2".equals(choice)) {
                if (page < totalPages - 1) {
                    page++;
                } else {
                    System.out.println("已经是最后一页。");
                }
            } else if ("0".equals(choice)) {
                return;
            } else {
                System.out.println("翻页输入无效，请输入 0、1 或 2。");
            }
        }
    }

    private static String[][] sortByDate(String[][] records) {
        String[][] sorted = new String[records.length][];
        for (int i = 0; i < records.length; i++) {
            sorted[i] = records[i];
        }

        for (int i = 0; i < sorted.length - 1; i++) {
            for (int j = 0; j < sorted.length - 1 - i; j++) {
                LocalDate first = CsvCleaner.parseDate(sorted[j][CsvCleaner.DATE]);
                LocalDate second = CsvCleaner.parseDate(sorted[j + 1][CsvCleaner.DATE]);
                if (first.isAfter(second)) {
                    String[] temp = sorted[j];
                    sorted[j] = sorted[j + 1];
                    sorted[j + 1] = temp;
                }
            }
        }
        return sorted;
    }

    public static String[][] addManually(String[][] records, Scanner scanner) {
        String[] prompts = {
                "id（数字）", "日期（三种格式之一）", "账户（微信/支付宝/校园卡/银行卡）",
                "类型（收入/支出）", "类别（纯汉字）", "金额（非负，最多两位小数）",
                "备注（可直接回车，不能含半角逗号）", "是否固定支出（Y/N）"
        };
        String[] fields = new String[CsvCleaner.FIELD_COUNT];
        System.out.println("请输入一笔账单；任意一步输入 q 或 :q 可取消并返回菜单。");
        for (int i = 0; i < fields.length; i++) {
            while (true) {
                System.out.print(prompts[i] + "：");
                if (!scanner.hasNextLine()) {
                    System.out.println("输入结束，本笔账单未添加。");
                    return records;
                }
                String value = scanner.nextLine();
                String command = value.trim();
                if ("q".equalsIgnoreCase(command) || ":q".equalsIgnoreCase(command)) {
                    System.out.println("已取消本笔记账。");
                    return records;
                }
                String error = CsvCleaner.checkField(i, value);
                if (error == null) {
                    fields[i] = value;
                    break;
                }
                System.out.println(error + "，请重新输入。");
            }
        }
        if (CsvCleaner.isDuplicate(fields, records, records.length)) {
            System.out.println("八字段完全重复，本笔未添加，保留已有记录。");
            return records;
        }

        String[][] added = new String[records.length + 1][];
        for (int i = 0; i < records.length; i++) {
            added[i] = records[i];
        }
        added[records.length] = fields;
        System.out.println("记账成功，当前账单数：" + added.length);
        return added;
    }

    public static void checkBudgetConfig(Properties config) {
        String categories = config.getProperty("budget.categories", "");
        if (categories.isEmpty()) {
            return;
        }
        String[] names = categories.split(",", -1);
        for (int i = 0; i < names.length; i++) {
            if (CsvCleaner.checkField(CsvCleaner.CATEGORY, names[i]) != null) {
                throw new IllegalArgumentException("budget.categories 中的类别必须为纯汉字。");
            }
            String amount = config.getProperty("budget." + names[i]);
            if (amount != null && CsvCleaner.checkField(CsvCleaner.AMOUNT, amount) != null) {
                throw new IllegalArgumentException("类别预算不合法：" + names[i]);
            }
        }
    }

    public static boolean hasBudget(Properties config, String category) {
        String[] names = config.getProperty("budget.categories", "").split(",", -1);
        return contains(names, names.length, category)
                && config.getProperty("budget." + category) != null;
    }

    public static BigDecimal getBudget(Properties config, String category) {
        if (!hasBudget(config, category)) {
            return null;
        }
        return new BigDecimal(config.getProperty("budget." + category)).setScale(MONEY_SCALE);
    }

    public static String[] getBudgetCategories(String[][] records, Properties config) {
        String[] configured = config.getProperty("budget.categories", "").split(",", -1);
        String[] names = new String[configured.length + records.length];
        int count = 0;
        for (int i = 0; i < configured.length; i++) {
            if (!configured[i].isEmpty() && !contains(names, count, configured[i])) {
                names[count] = configured[i];
                count++;
            }
        }
        for (int i = 0; i < records.length; i++) {
            String category = records[i][CsvCleaner.CATEGORY];
            if ("支出".equals(records[i][CsvCleaner.TYPE]) && !contains(names, count, category)) {
                names[count] = category;
                count++;
            }
        }
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = names[i];
        }
        return result;
    }

    private static boolean contains(String[] values, int count, String text) {
        for (int i = 0; i < count; i++) {
            if (text.equals(values[i])) {
                return true;
            }
        }
        return false;
    }

    public static void showBudgets(String[][] records, Properties config) {
        String[] categories = getBudgetCategories(records, config);
        if (categories.length == 0) {
            System.out.println("当前没有类别预算，可以输入类别添加。");
            return;
        }
        for (int i = 0; i < categories.length; i++) {
            BigDecimal budget = getBudget(config, categories[i]);
            if (budget == null) {
                System.out.println(categories[i] + "：没有设置预算");
            } else {
                System.out.println(categories[i] + "：" + budget.toPlainString() + " 元/月");
            }
        }
    }

    public static String[][] buildMonthlyReport(String[][] records, Properties config) {
        String[][] groups = new String[records.length][];
        int count = 0;
        for (int i = 0; i < records.length; i++) {
            if (!"支出".equals(records[i][CsvCleaner.TYPE])) {
                continue;
            }
            String month = YearMonth.from(CsvCleaner.parseDate(records[i][CsvCleaner.DATE])).toString();
            String category = records[i][CsvCleaner.CATEGORY];
            int index = -1;
            for (int j = 0; j < count; j++) {
                if (month.equals(groups[j][REPORT_MONTH]) && category.equals(groups[j][REPORT_CATEGORY])) {
                    index = j;
                    break;
                }
            }
            if (index == -1) {
                index = count;
                groups[count] = new String[] {month, category, "0.00", "未设置", "未判断"};
                count++;
            }
            BigDecimal total = new BigDecimal(groups[index][REPORT_EXPENSE]);
            groups[index][REPORT_EXPENSE] = total.add(new BigDecimal(records[i][CsvCleaner.AMOUNT])).toPlainString();
        }

        String[][] report = new String[count][];
        for (int i = 0; i < count; i++) {
            report[i] = groups[i];
            BigDecimal budget = getBudget(config, report[i][REPORT_CATEGORY]);
            if (budget != null) {
                report[i][REPORT_BUDGET] = budget.toPlainString();
                report[i][REPORT_OVER] = "否";
                if (new BigDecimal(report[i][REPORT_EXPENSE]).compareTo(budget) > 0) {
                    report[i][REPORT_OVER] = "是";
                }
            }
        }
        for (int i = 0; i < report.length - 1; i++) {
            for (int j = 0; j < report.length - 1 - i; j++) {
                int monthOrder = report[j][REPORT_MONTH].compareTo(report[j + 1][REPORT_MONTH]);
                if (monthOrder > 0 || (monthOrder == 0
                        && report[j][REPORT_CATEGORY].compareTo(report[j + 1][REPORT_CATEGORY]) > 0)) {
                    String[] temp = report[j];
                    report[j] = report[j + 1];
                    report[j + 1] = temp;
                }
            }
        }
        return report;
    }

    public static void showMonthlyReport(String[][] records, Properties config) {
        String[][] report = buildMonthlyReport(records, config);
        if (report.length == 0) {
            System.out.println("当前没有支出记录，暂无月度分类支出。");
            return;
        }
        System.out.println("月份 | 类别 | 支出金额 | 预算 | 是否超支");
        for (int i = 0; i < report.length; i++) {
            System.out.println(String.join(" | ", report[i]));
            if (!hasBudget(config, report[i][REPORT_CATEGORY])) {
                System.out.println("提示：" + report[i][REPORT_CATEGORY]
                        + "没有设置预算，暂不判断是否超支，可在菜单 6 选择是否添加。");
            }
            showBudgetWarning(report[i]);
        }
    }

    public static void showBudgetWarnings(String[][] records, Properties config) {
        String[][] report = buildMonthlyReport(records, config);
        for (int i = 0; i < report.length; i++) {
            showBudgetWarning(report[i]);
        }
    }

    private static void showBudgetWarning(String[] row) {
        if ("是".equals(row[REPORT_OVER])) {
            BigDecimal excess = new BigDecimal(row[REPORT_EXPENSE]).subtract(new BigDecimal(row[REPORT_BUDGET]));
            System.out.println("!!! 超支警告：" + row[REPORT_MONTH] + " " + row[REPORT_CATEGORY]
                    + " 超出预算 " + excess.toPlainString() + " 元 !!!");
        }
    }
}
