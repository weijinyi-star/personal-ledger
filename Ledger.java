package cleaner;

import java.math.BigDecimal;

public class Ledger {
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
}
