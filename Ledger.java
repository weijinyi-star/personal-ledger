package cleaner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Scanner;

public class Ledger {
    private static final int PAGE_SIZE = 10;

    public static void showSummary(String[][] records) {
        if (records.length == 0) {
            System.out.println("当前没有有效账单，请先导入包含有效记录的 CSV。");
            return;
        }

        BigDecimal income = new BigDecimal("0.00");
        BigDecimal expense = new BigDecimal("0.00");
        for (int i = 0; i < records.length; i++) {
            BigDecimal amount = new BigDecimal(records[i][5]);
            if ("收入".equals(records[i][3])) {
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
            System.out.println("id | 日期 | 账户 | 类型 | 类别 | 金额 | 备注 | recurring");
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
                LocalDate first = CsvCleaner.parseDate(sorted[j][1]);
                LocalDate second = CsvCleaner.parseDate(sorted[j + 1][1]);
                if (first.isAfter(second)) {
                    String[] temp = sorted[j];
                    sorted[j] = sorted[j + 1];
                    sorted[j + 1] = temp;
                }
            }
        }
        return sorted;
    }
}
