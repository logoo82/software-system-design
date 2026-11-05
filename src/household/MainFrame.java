package household;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import static household.Domain.*;
import static household.Commands.*;
import household.Commands.Action;
import static household.AccountService.*;

/**
 * 가계부 화면과 버튼 동작을 모아둔 클래스.
 * 입력은 여기서 받고, 계산이나 저장이 필요한 처리는 AccountService에 넘긴다.
 * @author 김승혁
 * @version 0.4
 */
public final class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final AccountService service;
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JSpinner year;
    private final JSpinner month;
    private final JLabel notice = new JLabel(" ");

    private final JLabel dashboard = new JLabel();
    private final JLabel forecast = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 100);

    private final JTextField budget = new JTextField(12);
    private final JLabel budgetSummary = new JLabel();

    private final JLabel analysisInfo = new JLabel();
    private final JLabel savings = new JLabel(" ");
    private final JComboBox<String> reductionCategory =
            new JComboBox<>(CATEGORIES.toArray(new String[0]));
    private final JSpinner reduction = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));

    private final JTextField txFrom = new JTextField(10);
    private final JTextField txTo = new JTextField(10);
    private final JTextField keyword = new JTextField(12);
    private final JComboBox<String> txType = new JComboBox<>(new String[] {"전체", "수입", "지출"});
    private final JComboBox<String> txCategory = new JComboBox<>(withAll());

    private final JTextField nsFrom = new JTextField(10);
    private final JTextField nsTo = new JTextField(10);
    private final JLabel nsTotal = new JLabel();
    private final JTextArea receipt = new JTextArea();

    private final JTable txTable = table("날짜", "유형", "카테고리", "금액 (원)", "상세 내용");
    private final JTable analysisTable =
            table("카테고리", "현재 지출 (원)", "비율", "직전 평균 (원)", "증감 (원)", "증감률");
    private final JTable goalTable =
            table("목표", "목표액 (원)", "실제 저축 (원)", "가상 절약 (원)", "실제 달성률", "가상 진척도", "남은 금액 (원)");
    private final JTable nsTable = table("날짜", "참은 소비", "예정 금액 (원)", "연결 목표", "실제 저축 반영");
    private final JButton linkButton = new JButton("목표 연결");
    private final JButton applyButton = new JButton("실제 저축액 반영");

    private List<Entry> displayedEntries = List.of();
    private List<GoalStatus> displayedGoals = List.of();
    private List<NoSpend> displayedNoSpends = List.of();

    /**
     * 메뉴와 각 화면을 만들고, 처음 선택한 달의 내용을 보여준다.
     * @param service 입력값을 처리하고 계산할 객체
     * @param demo 예시 데이터로 실행 중인지 여부
     */
    public MainFrame(AccountService service, boolean demo) {
        super("Smart Household Account Book  |  Beta 0.4" + (demo ? "  |  데모" : ""));
        this.service = service;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1180, 800);
        setMinimumSize(new Dimension(980, 680));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 12, 18));
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Smart Household Account Book");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        top.add(title, BorderLayout.WEST);

        YearMonth now = YearMonth.from(service.today());
        year = new JSpinner(new SpinnerNumberModel(now.getYear(), 2000, 2100, 1));
        year.setEditor(new JSpinner.NumberEditor(year, "####"));
        month = new JSpinner(new SpinnerNumberModel(now.getMonthValue(), 1, 12, 1));
        JPanel dates =
                flow(
                        new JLabel("조회 월"),
                        year,
                        new JLabel("년"),
                        month,
                        new JLabel("월"),
                        new JLabel("   기준일 " + service.today() + (demo ? " (예시 데이터)" : "")));
        top.add(dates, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // 왼쪽 메뉴를 만들고 버튼마다 이동할 화면을 연결한다.
        JPanel menu = new JPanel(new GridLayout(7, 1, 0, 9));
        menu.setPreferredSize(new Dimension(150, 0));
        String[] names = {"대시보드", "거래 내역", "월별 예산", "소비 분석", "미래 목표", "꾹참기"};
        for (String name : names) {
            JButton b = new JButton(name);
            b.addActionListener(e -> selectPage(name));
            menu.add(b);
        }
        menu.add(new JLabel("223247  김승혁", SwingConstants.CENTER));
        root.add(menu, BorderLayout.WEST);
        root.add(cards, BorderLayout.CENTER);

        // 화면은 미리 만들어두고 메뉴를 누르면 하나씩 보여준다.
        cards.add(dashboardPanel(), "대시보드");
        cards.add(transactionPanel(), "거래 내역");
        cards.add(budgetPanel(), "월별 예산");
        cards.add(analysisPanel(), "소비 분석");
        cards.add(goalPanel(), "미래 목표");
        cards.add(noSpendPanel(), "꾹참기");

        JPanel footer = new JPanel(new BorderLayout());
        notice.setForeground(new Color(28, 80, 110));
        footer.add(notice, BorderLayout.NORTH);
        root.add(footer, BorderLayout.SOUTH);

        year.addChangeListener(e -> perform(this::refreshAll));
        month.addChangeListener(e -> perform(this::refreshAll));

        perform(this::refreshAll);
    }

    /**
     * 선택한 메뉴에 맞는 화면으로 바꾼다.
     * @param name 대시보드, 거래 내역, 월별 예산, 소비 분석, 미래 목표, 꾹참기
     */
    public void selectPage(String name) {
        layout.show(cards, name);
    }

    private JPanel dashboardPanel() {
        JPanel p = page("월별 재정 현황");
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        dashboard.setFont(dashboard.getFont().deriveFont(19f));
        content.add(dashboard);
        content.add(Box.createVerticalStrut(24));

        progress.setStringPainted(true);
        progress.setMaximumSize(new Dimension(1000, 30));
        content.add(progress);
        content.add(Box.createVerticalStrut(20));

        forecast.setFont(forecast.getFont().deriveFont(17f));
        content.add(forecast);

        p.add(content, BorderLayout.CENTER);

        return p;
    }

    private JPanel transactionPanel() {
        JPanel p = page("거래 내역 관리");

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 8));
        header.add(
                flow(
                        new JLabel("시작일"),
                        txFrom,
                        new JLabel("종료일"),
                        txTo,
                        new JLabel("유형"),
                        txType,
                        new JLabel("카테고리"),
                        txCategory));
        header.add(
                flow(
                        new JLabel("상세 내용"),
                        keyword,
                        button(
                                "검색",
                                () -> {
                                    refreshTransactions();
                                    notice.setText("검색 결과 " + displayedEntries.size() + "건");
                                }),
                        button(
                                "초기화",
                                () -> {
                                    txFrom.setText("");
                                    txTo.setText("");
                                    keyword.setText("");
                                    txType.setSelectedIndex(0);
                                    txCategory.setSelectedIndex(0);
                                    refreshTransactions();
                                }),
                        new JLabel("YYYY-MM-DD")));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.add(header, BorderLayout.NORTH);
        center.add(new JScrollPane(txTable), BorderLayout.CENTER);
        p.add(center);

        p.add(
                flow(
                        button("거래 추가", () -> editEntry(null)),
                        button("선택 수정", () -> editEntry(selectedEntry())),
                        button(
                                "선택 삭제",
                                () -> {
                                    Entry e = selectedEntry();
                                    if (confirm("선택한 거래를 삭제할까요?")) {
                                        service.manageTransaction(
                                                new TransactionCommand(
                                                        Action.DELETE,
                                                        e.getId(),
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null));
                                        changed("거래 삭제함.");
                                    }
                                })),
                BorderLayout.SOUTH);

        return p;
    }

    private JPanel budgetPanel() {
        JPanel p = page("월별 예산 설정");
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(
                flow(
                        new JLabel("선택 월 예산 (원)"),
                        budget,
                        button(
                                "예산 저장",
                                () -> {
                                    service.manageMonthlyFinance(
                                            new BudgetCommand(
                                                    Action.SET_BUDGET,
                                                    selectedMonth(),
                                                    number(budget.getText())));
                                    changed("예산 저장함.");
                                })));

        body.add(Box.createVerticalStrut(24));
        budgetSummary.setFont(budgetSummary.getFont().deriveFont(18f));
        body.add(budgetSummary);

        body.getComponent(0).setMaximumSize(new Dimension(1000, 48));
        for (Component c : body.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        p.add(body);

        return p;
    }

    private JPanel analysisPanel() {
        JPanel p = page("소비 패턴과 절감 효과");

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(analysisInfo, BorderLayout.NORTH);
        center.add(new JScrollPane(analysisTable));
        p.add(center);

        JPanel bottom = new JPanel(new GridLayout(2, 1));
        bottom.add(
                flow(
                        new JLabel("절감 카테고리"),
                        reductionCategory,
                        new JLabel("절감 비율"),
                        reduction,
                        new JLabel("%"),
                        button("계산", () -> refreshAnalysis(true))));
        bottom.add(savings);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private JPanel goalPanel() {
        JPanel p = page("미래 목표와 실제 저축");
        p.add(new JScrollPane(goalTable));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(
                flow(
                        button("목표 추가", () -> editGoal(null)),
                        button("선택 수정", () -> editGoal(selectedGoal())),
                        button(
                                "저축액 추가",
                                () -> {
                                    Goal g = selectedGoal();
                                    String value =
                                            JOptionPane.showInputDialog(
                                                    this,
                                                    "추가 저축액 (원)\n꾹참기 반영액은 제외함.");
                                    if (value != null) {
                                        service.manageGoal(
                                                new GoalCommand(
                                                        Action.ADD_SAVING,
                                                        g.getId(),
                                                        null,
                                                        null,
                                                        number(value)));
                                        changed("저축액 추가함.");
                                    }
                                }),
                        button(
                                "선택 삭제",
                                () -> {
                                    Goal g = selectedGoal();
                                    if (confirm("목표를 삭제할까요?\n꾹참기 기록은 유지됨.")) {
                                        service.manageGoal(
                                                new GoalCommand(
                                                        Action.DELETE,
                                                        g.getId(),
                                                        null,
                                                        null,
                                                        null));
                                        changed("목표 삭제함.");
                                    }
                                })),
                BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel noSpendPanel() {
        JPanel p = page("꾹참기와 대체 소비 영수증");
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(
                flow(
                        new JLabel("시작일"),
                        nsFrom,
                        new JLabel("종료일"),
                        nsTo,
                        button("조회", this::refreshNoSpends),
                        button(
                                "전체",
                                () -> {
                                    nsFrom.setText("");
                                    nsTo.setText("");
                                    refreshNoSpends();
                                })),
                BorderLayout.NORTH);

        receipt.setEditable(false);
        receipt.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        receipt.setMargin(new Insets(12, 12, 12, 12));
        receipt.setLineWrap(true);
        receipt.setWrapStyleWord(true);

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        new JScrollPane(nsTable),
                        new JScrollPane(receipt));
        split.setResizeWeight(0.42);
        center.add(split);
        p.add(center);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(nsTotal, BorderLayout.NORTH);
        bottom.add(
                flow(button("꾹참기 등록", this::addNoSpend), linkButton, applyButton),
                BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);

        linkButton.addActionListener(e -> perform(this::linkGoal));
        applyButton.addActionListener(e -> perform(this::applySaving));
        nsTable.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        updateReceipt();
                    }
                });

        return p;
    }

    // 기존 거래가 없으면 추가하고, 선택한 거래가 있으면 수정한다.
    private void editEntry(Entry old) {
        JTextField date =
                new JTextField(
                        old == null ? service.today().toString() : old.getDate().toString(), 16);
        JComboBox<String> type = new JComboBox<>(new String[] {"지출", "수입"});
        if (old != null) {
            type.setSelectedIndex(old.getType() == Domain.Type.EXPENSE ? 0 : 1);
        }
        JComboBox<String> category = new JComboBox<>(CATEGORIES.toArray(new String[0]));
        if (old != null) {
            category.setSelectedItem(old.getCategory());
        }
        JTextField amount = new JTextField(old == null ? "" : old.getAmount().toPlainString());
        JTextField description = new JTextField(old == null ? "" : old.getDescription());
        JComponent[] fields = {date, type, category, amount, description};

        if (form(
                old == null ? "거래 추가" : "거래 수정",
                new String[] {"날짜 (YYYY-MM-DD)", "유형", "카테고리", "금액 (원)", "상세 내용 (200자 이하)"},
                fields)) {
            service.manageTransaction(
                    new TransactionCommand(
                            old == null ? Action.CREATE : Action.UPDATE,
                            old == null ? null : old.getId(),
                            parseDate(date.getText()),
                            type.getSelectedIndex() == 0 ? Domain.Type.EXPENSE : Domain.Type.INCOME,
                            (String) category.getSelectedItem(),
                            number(amount.getText()),
                            description.getText(),
                            null,
                            null,
                            null));
            changed("거래 저장함.");
        }
    }

    // 목표 입력창에서는 새로 모은 금액이 아니라 현재 저축 총액을 받는다.
    private void editGoal(Goal old) {
        JTextField name = new JTextField(old == null ? "" : old.getName(), 18);
        JTextField target = new JTextField(old == null ? "" : old.getTarget().toPlainString());
        JTextField actual = new JTextField(old == null ? "0" : old.getActual().toPlainString());

        if (form(
                old == null ? "목표 추가" : "목표 수정 (실제 저축액은 새 총액)",
                new String[] {"목표 이름 (1~50자)", "목표 금액 (원)", "현재 실제 저축 총액 (원)"},
                new JComponent[] {name, target, actual})) {
            service.manageGoal(
                    new GoalCommand(
                            old == null ? Action.CREATE : Action.UPDATE,
                            old == null ? null : old.getId(),
                            name.getText(),
                            number(target.getText()),
                            number(actual.getText())));
            changed("목표 저장함.");
        }
    }

    // 참은 소비를 기록한다. 여기서는 실제 저축액이 늘지 않는다.
    private void addNoSpend() {
        JTextField item = new JTextField(18);
        JTextField amount = new JTextField();
        JTextField date = new JTextField(service.today().toString());
        JComboBox<GoalChoice> goals = goalChoices();

        if (form(
                "참은 소비 기록",
                new String[] {"소비 이름 (1~50자)", "예정 금액 (원)", "날짜 (YYYY-MM-DD)", "연결 목표 (선택 사항)"},
                new JComponent[] {item, amount, date, goals})) {
            GoalChoice selected = (GoalChoice) goals.getSelectedItem();
            List<NoSpend> result =
                    service.manageNoSpend(
                            new NoSpendCommand(
                                    Action.REGISTER,
                                    null,
                                    item.getText(),
                                    number(amount.getText()),
                                    parseDate(date.getText()),
                                    selected.id,
                                    null,
                                    null));
            changed("꾹참기 기록함.");
            selectNoSpend(result.get(0).getId());
        }
    }

    private void linkGoal() {
        NoSpend n = selectedNoSpend();
        JComboBox<GoalChoice> choices = goalChoices();

        if (form("기존 꾹참기에 목표 연결", new String[] {"연결할 목표"}, new JComponent[] {choices})) {
            GoalChoice g = (GoalChoice) choices.getSelectedItem();
            if (g.id == null) {
                throw new AppException(AppException.Code.GOAL_REQUIRED, "연결할 목표를 선택해야 함.");
            }
            service.manageNoSpend(
                    new NoSpendCommand(
                            Action.LINK_GOAL, n.getId(), null, null, null, g.id, null, null));
            changed("목표 연결함.");
            selectNoSpend(n.getId());
        }
    }

    // 실제로 저축했는지 한 번 더 물어본 뒤 목표에 반영한다.
    private void applySaving() {
        NoSpend n = selectedNoSpend();

        if (confirm(won(n.getAmount()) + "원을 실제 저축에 반영할까요?")) {
            service.manageNoSpend(
                    new NoSpendCommand(
                            Action.APPLY_ACTUAL, n.getId(), null, null, null, null, null, null));
            changed("실제 저축에 반영함.");
            selectNoSpend(n.getId());
        }
    }

    // 조회한 달의 내용을 다시 계산해서 화면과 표를 갱신한다.
    private void refreshAll() {
        Summary s =
                service.manageMonthlyFinance(
                        new BudgetCommand(Action.QUERY, selectedMonth(), null));
        boolean future = s.getIncome() == null;

        dashboard.setText(
                future
                        ? "미래 월은 예산만 설정 가능함."
                        : "<html>총수입　"
                                + won(s.getIncome())
                                + "원<br><br>총지출　"
                                + won(s.getExpense())
                                + "원<br><br>월간 잔액　"
                                + won(s.getBalance())
                                + "원</html>");

        progress.setValue(s.getUsage() == null ? 0 : Math.min(100, s.getUsage().intValue()));
        progress.setString(
                s.getUsage() == null
                        ? "예산 미설정 / 미래 월"
                        : "예산 사용률 "
                                + one(s.getUsage())
                                + "%  |  남은 예산 "
                                + won(s.getRemaining())
                                + "원");

        forecast.setText(
            future
                ? " "
                : "<html>월말 예상 지출　"
                    + won(s.getForecast())
                    + "원<br><br>"
                    + (s.getBudget() == null
                        ? "예산 미설정"
                        : s.getOverrun().signum() > 0
                            ? "<font color='#A53929'>예산 초과 "
                                + (selectedMonth().equals(YearMonth.from(service.today()))
                                    ? "예상 "
                                    : "")
                                + won(s.getOverrun())
                                + "원</font>"
                            : "예산 초과 없음.")
                    + "</html>"
        );

        budget.setText(s.getBudget() == null ? "" : s.getBudget().toPlainString());
        budgetSummary.setText(
                future
                        ? "미래 월 예산: " + won(s.getBudget())
                        : "<html>총지출　"
                                + won(s.getExpense())
                                + "원<br><br>남은 예산　"
                                + won(s.getRemaining())
                                + (s.getRemaining() == null ? "" : "원")
                                + "<br><br>사용률　"
                                + one(s.getUsage())
                                + "%<br><br>예상 지출　"
                                + won(s.getForecast())
                                + "원</html>");

        refreshTransactions();
        refreshGoals();
        refreshNoSpends();
        refreshAnalysis(false);
    }

    private void refreshTransactions() {
        Domain.Type type =
                txType.getSelectedIndex() == 0
                        ? null
                        : txType.getSelectedIndex() == 1 ? Domain.Type.INCOME : Domain.Type.EXPENSE;
        displayedEntries =
                service.manageTransaction(
                        new TransactionCommand(
                                Action.QUERY,
                                null,
                                null,
                                type,
                                txCategory.getSelectedIndex() == 0
                                        ? null
                                        : (String) txCategory.getSelectedItem(),
                                null,
                                null,
                                optionalDate(txFrom.getText()),
                                optionalDate(txTo.getText()),
                                keyword.getText()));

        DefaultTableModel m = model(txTable);
        m.setRowCount(0);
        for (Entry e : displayedEntries) {
            m.addRow(
                    new Object[] {
                        e.getDate(),
                        e.getType() == Domain.Type.INCOME ? "수입" : "지출",
                        e.getCategory(),
                        won(e.getAmount()),
                        e.getDescription()
                    });
        }
    }

    private void refreshGoals() {
        displayedGoals = service.manageGoal(new GoalCommand(Action.QUERY, null, null, null, null));

        DefaultTableModel m = model(goalTable);
        m.setRowCount(0);
        for (GoalStatus g : displayedGoals) {
            m.addRow(
                    new Object[] {
                        g.getGoal().getName(),
                        won(g.getGoal().getTarget()),
                        won(g.getGoal().getActual()),
                        won(g.getVirtual()),
                        one(g.getActualRate()) + "%",
                        one(g.getVirtualRate()) + "%",
                        won(g.getRemaining())
                    });
        }
    }

    private void refreshNoSpends() {
        displayedNoSpends =
                service.manageNoSpend(
                        new NoSpendCommand(
                                Action.QUERY,
                                null,
                                null,
                                null,
                                null,
                                null,
                                optionalDate(nsFrom.getText()),
                                optionalDate(nsTo.getText())));

        DefaultTableModel m = model(nsTable);
        m.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        for (NoSpend n : displayedNoSpends) {
            total = total.add(n.getAmount());
            m.addRow(
                    new Object[] {
                        n.getDate(),
                        n.getItem(),
                        won(n.getAmount()),
                        goalName(n.getGoalId()),
                        n.getApplied() ? "반영 완료" : "미반영"
                    });
        }

        nsTotal.setText(
                displayedNoSpends.size()
                        + "건  |  가상 절약 합계 "
                        + won(total)
                        + "원");
        if (!displayedNoSpends.isEmpty()) {
            nsTable.setRowSelectionInterval(0, 0);
        } else {
            updateReceipt();
        }
    }

    private void refreshAnalysis(boolean calculate) {
        DefaultTableModel m = model(analysisTable);
        m.setRowCount(0);

        if (selectedMonth().isAfter(YearMonth.from(service.today()))) {
            analysisInfo.setText("미래 월은 분석할 수 없음.");
            savings.setText(" ");
            return;
        }

        Analysis a =
                service.analyzeSpending(
                        selectedMonth(),
                        calculate ? (String) reductionCategory.getSelectedItem() : null,
                        calculate
                                ? BigDecimal.valueOf((Integer) reduction.getValue())
                                        .movePointLeft(2)
                                : null);

        analysisInfo.setText(
                a.getEmpty()
                        ? "지출 내역 없음."
                        : selectedMonth()
                                + " 누적 / 비교 "
                                + a.getBaselineMonths()
                                + "개월 / 예상 지출 "
                                + won(a.getForecast())
                                + "원");

        for (CategoryStat c : a.getCategories()) {
            m.addRow(
                    new Object[] {
                        c.getCategory(),
                        won(c.getSpent()),
                        one(c.getShare()) + "%",
                        c.getAverage() == null ? "비교 불가" : won(c.getAverage()),
                        c.getDelta() == null ? "-" : won(c.getDelta()),
                        c.getRate() != null
                                ? one(c.getRate()) + "%"
                                : c.getAverage() == null
                                        ? "비교 불가"
                                        : c.getSpent().signum() > 0 ? "신규 지출" : "변동 없음"
                    });
        }
        savings.setText(
                calculate && !a.getEmpty()
                        ? "월간 절감 "
                                + won(a.getMonthlySaving())
                                + "원 / 연간 절감 "
                                + won(a.getAnnualSaving())
                                + "원"
                        : " ");
    }

    private void updateReceipt() {
        int row = nsTable.getSelectedRow();
        NoSpend n =
                row < 0 || row >= displayedNoSpends.size()
                        ? null
                        : displayedNoSpends.get(nsTable.convertRowIndexToModel(row));
        receipt.setText(n == null ? "선택한 기록 없음." : service.receipt(n.getId()));
        receipt.setCaretPosition(0);
        // 선택한 기록에서 할 수 있는 작업만 버튼을 켜둔다.
        linkButton.setEnabled(
                n != null
                        && !n.getApplied()
                        && n.getGoalId() == null
                        && !service.snapshot().getGoals().isEmpty());
        applyButton.setEnabled(n != null && !n.getApplied() && n.getGoalId() != null);
    }

    private void selectNoSpend(UUID id) {
        for (int i = 0; i < displayedNoSpends.size(); i++) {
            if (displayedNoSpends.get(i).getId().equals(id)) {
                int r = nsTable.convertRowIndexToView(i);
                nsTable.setRowSelectionInterval(r, r);
                return;
            }
        }
    }

    private String goalName(UUID id) {
        return service.snapshot().getGoals().stream()
                .filter(g -> g.getId().equals(id))
                .map(Goal::getName)
                .findFirst()
                .orElse("연결 없음");
    }

    private JComboBox<GoalChoice> goalChoices() {
        JComboBox<GoalChoice> box = new JComboBox<>();
        box.addItem(new GoalChoice(null, "선택 안 함"));
        for (Goal g : service.snapshot().getGoals()) {
            box.addItem(new GoalChoice(g.getId(), g.getName()));
        }
        return box;
    }

    private static final class GoalChoice {
        final UUID id;
        final String name;

        GoalChoice(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    // 표를 정렬했어도 사용자가 선택한 거래를 찾아준다.
    private Entry selectedEntry() {
        int r = txTable.getSelectedRow();

        if (r < 0) {
            throw selectError();
        }
        return displayedEntries.get(txTable.convertRowIndexToModel(r));
    }

    private Goal selectedGoal() {
        int r = goalTable.getSelectedRow();

        if (r < 0) {
            throw selectError();
        }
        return displayedGoals.get(goalTable.convertRowIndexToModel(r)).getGoal();
    }

    private NoSpend selectedNoSpend() {
        int r = nsTable.getSelectedRow();

        if (r < 0) {
            throw selectError();
        }
        return displayedNoSpends.get(nsTable.convertRowIndexToModel(r));
    }

    private AppException selectError() {
        return new AppException(AppException.Code.VALIDATION_ERROR, "선택한 항목 없음.");
    }

    private YearMonth selectedMonth() {
        return YearMonth.of((Integer) year.getValue(), (Integer) month.getValue());
    }

    private void changed(String text) {
        refreshAll();
        notice.setText(text);
    }

    private boolean confirm(String text) {
        return JOptionPane.showConfirmDialog(this, text, "확인", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    private boolean form(String title, String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridLayout(labels.length, 2, 12, 12));
        for (int i = 0; i < labels.length; i++) {
            p.add(new JLabel(labels[i]));
            p.add(fields[i]);
        }

        return JOptionPane.showConfirmDialog(
                        this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> perform(action));
        return b;
    }

    // 버튼 처리를 하다가 문제가 생기면 화면에 안내한다.
    private void perform(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            JOptionPane.showMessageDialog(
                    this, e.getMessage(), "오류", JOptionPane.WARNING_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "날짜: YYYY-MM-DD / 금액: 정수 원",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static BigDecimal number(String s) {
        return new BigDecimal(s.trim().replace(",", ""));
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new AppException(
                    AppException.Code.VALIDATION_ERROR, "날짜 형식: YYYY-MM-DD");
        }
    }

    private static LocalDate optionalDate(String s) {
        return s.isBlank() ? null : parseDate(s);
    }

    private static String[] withAll() {
        List<String> values = new ArrayList<>();
        values.add("전체");
        values.addAll(CATEGORIES);
        return values.toArray(new String[0]);
    }

    private static JPanel flow(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    private static JPanel page(String title) {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        JLabel h = new JLabel(title);
        h.setFont(h.getFont().deriveFont(Font.BOLD, 20f));
        p.add(h, BorderLayout.NORTH);
        return p;
    }

    private static JTable table(String... columns) {
        DefaultTableModel model =
                new DefaultTableModel(columns, 0) {
                    private static final long serialVersionUID = 1L;

                    public boolean isCellEditable(int row, int col) {
                        return false;
                    }
                };

        JTable t = new JTable(model);
        t.setRowHeight(32);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    private static DefaultTableModel model(JTable table) {
        return (DefaultTableModel) table.getModel();
    }
}
