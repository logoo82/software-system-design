package household;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static household.Domain.*;
import static household.Commands.*;
import static household.AppException.Code.*;

/** 외부 테스트 라이브러리 없이 실행하는 베타 핵심 시나리오 검사. */
public final class ServiceTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static int checks;

    private ServiceTest() {}

    /**
     * 계산·검증·변경·저장 실패·재로딩·동시 요청을 검사한다.
     * @param args 테스트 작업 폴더
     * @throws Exception 테스트 실패 또는 파일 오류
     */
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length == 0 ? "build/test-data" : args[0]);
        Files.createDirectories(dir);

        Path folder = Files.createTempDirectory(dir, "run-");
        Path file = folder.resolve("ledger.dat");

        try (DataRepository repo = new DataRepository(file)) {
            AccountService s = new AccountService(repo, CLOCK);
            eq(s.snapshot().getEntries().size(), 0, "first run empty");

            Summary blank =
                    s.manageMonthlyFinance(
                            new BudgetCommand(Action.QUERY, YearMonth.of(2026, 10), null));
            eq(blank.getBudget(), null, "budget unset");
            eq(blank.getUsage(), null, "unset rate not division by zero");
            amount(blank.getExpense(), "0", "empty expenses zero");

            expect(
                    VALIDATION_ERROR,
                    () ->
                            s.manageMonthlyFinance(
                                    new BudgetCommand(
                                            Action.SET_BUDGET,
                                            YearMonth.of(2026, 10),
                                            BigDecimal.ZERO)),
                    "zero budget rejected");
        }

        try (DataRepository repo = new DataRepository(file)) {
            repo.commit(DemoData.create());
        }

        try (DataRepository repo = new DataRepository(file)) {
            AccountService s = new AccountService(repo, CLOCK);
            Summary sum =
                    s.manageMonthlyFinance(
                            new BudgetCommand(Action.QUERY, YearMonth.of(2026, 10), null));
            amount(sum.getIncome(), "1000000", "income");
            amount(sum.getExpense(), "300000", "expense");
            amount(sum.getBalance(), "700000", "balance");
            amount(sum.getRemaining(), "200000", "remaining budget");
            amount(sum.getUsage(), "60", "usage");
            amount(sum.getForecast(), "620000", "forecast");
            amount(sum.getOverrun(), "120000", "expected overrun");

            Summary past =
                    s.manageMonthlyFinance(
                            new BudgetCommand(Action.QUERY, YearMonth.of(2026, 9), null));
            amount(past.getForecast(), "270000", "past final expense");

            Summary future =
                    s.manageMonthlyFinance(
                            new BudgetCommand(Action.QUERY, YearMonth.of(2026, 11), null));
            eq(future.getForecast(), null, "future no forecast");

            s.manageMonthlyFinance(
                    new BudgetCommand(Action.SET_BUDGET, YearMonth.of(2026, 11), b("700000")));
            amount(
                    s.manageMonthlyFinance(
                                    new BudgetCommand(Action.QUERY, YearMonth.of(2026, 11), null))
                            .getBudget(),
                    "700000",
                    "future budget saved");

            Analysis a = s.analyzeSpending(YearMonth.of(2026, 10), "식비", b("0.20"));
            eq(a.getBaselineMonths(), 3, "three baseline months");
            CategoryStat food =
                    a.getCategories().stream()
                            .filter(x -> x.getCategory().equals("식비"))
                            .findFirst()
                            .orElseThrow();
            amount(food.getSpent(), "150000", "food sum");
            amount(food.getShare(), "50", "food share");
            amount(food.getAverage(), "120000", "food average");
            amount(food.getRate(), "25", "change rate");
            amount(a.getMonthlySaving(), "30000", "monthly saving");
            amount(a.getAnnualSaving(), "360000", "annual saving");

            expect(
                    INVALID_MONTH,
                    () -> s.analyzeSpending(YearMonth.of(2026, 11), null, null),
                    "future analysis rejected");
            expect(
                    VALIDATION_ERROR,
                    () -> s.analyzeSpending(YearMonth.of(2026, 10), "식비", b("0")),
                    "zero reduction rejected");
            expect(
                    VALIDATION_ERROR,
                    () -> s.analyzeSpending(YearMonth.of(2026, 10), "식비", b("1.01")),
                    "over 100 percent rejected");
            expect(
                    VALIDATION_ERROR,
                    () -> s.analyzeSpending(YearMonth.of(2026, 10), "식비", null),
                    "incomplete reduction rejected");

            List<Entry> query =
                    s.manageTransaction(
                            new TransactionCommand(
                                    Action.QUERY,
                                    null,
                                    null,
                                    Type.EXPENSE,
                                    "식비",
                                    null,
                                    null,
                                    LocalDate.of(2026, 10, 1),
                                    LocalDate.of(2026, 10, 31),
                                    "점심"));
            eq(query.size(), 1, "AND search");
            Entry entry = query.get(0);

            s.manageTransaction(
                    new TransactionCommand(
                            Action.UPDATE,
                            entry.getId(),
                            entry.getDate(),
                            entry.getType(),
                            entry.getCategory(),
                            b("20000"),
                            "수정 점심",
                            null,
                            null,
                            null));
            amount(
                    s.manageMonthlyFinance(
                                    new BudgetCommand(Action.QUERY, YearMonth.of(2026, 10), null))
                            .getExpense(),
                    "310000",
                    "update recalculates");

            s.manageTransaction(
                    new TransactionCommand(
                            Action.UPDATE,
                            entry.getId(),
                            entry.getDate(),
                            entry.getType(),
                            entry.getCategory(),
                            entry.getAmount(),
                            entry.getDescription(),
                            null,
                            null,
                            null));
            int count = s.snapshot().getEntries().size();

            s.manageTransaction(
                    new TransactionCommand(
                            Action.CREATE,
                            null,
                            LocalDate.of(2026, 10, 15),
                            Type.EXPENSE,
                            "쇼핑",
                            b("1000"),
                            "delete me",
                            null,
                            null,
                            null));
            Entry added =
                    s.snapshot().getEntries().stream()
                            .filter(e -> e.getDescription().equals("delete me"))
                            .findFirst()
                            .orElseThrow();
            s.manageTransaction(
                    new TransactionCommand(
                            Action.DELETE,
                            added.getId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
            eq(s.snapshot().getEntries().size(), count, "create delete");

            expect(
                    VALIDATION_ERROR,
                    () ->
                            s.manageTransaction(
                                    new TransactionCommand(
                                            Action.CREATE,
                                            null,
                                            LocalDate.of(2026, 10, 16),
                                            Type.EXPENSE,
                                            "식비",
                                            b("1"),
                                            "",
                                            null,
                                            null,
                                            null)),
                    "future entry rejected");
            expect(
                    VALIDATION_ERROR,
                    () ->
                            s.manageTransaction(
                                    new TransactionCommand(
                                            Action.CREATE,
                                            null,
                                            LocalDate.of(2026, 10, 15),
                                            Type.EXPENSE,
                                            "식비",
                                            b("1.5"),
                                            "",
                                            null,
                                            null,
                                            null)),
                    "fractional won rejected");
            expect(
                    VALIDATION_ERROR,
                    () ->
                            s.manageTransaction(
                                    new TransactionCommand(
                                            Action.QUERY,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            LocalDate.of(2026, 10, 15),
                                            LocalDate.of(2026, 10, 1),
                                            null)),
                    "invalid date range");
            expect(
                    NOT_FOUND,
                    () ->
                            s.manageTransaction(
                                    new TransactionCommand(
                                            Action.DELETE,
                                            UUID.randomUUID(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null)),
                    "missing entry");

            NoSpend n = s.snapshot().getNoSpends().get(0);
            Goal g = s.snapshot().getGoals().get(0);
            amount(g.getActual(), "500000", "virtual register leaves actual unchanged");
            String receipt = s.receipt(n.getId());
            eq(receipt.contains("1.5%"), true, "receipt goal ratio");

            s.manageNoSpend(
                    new NoSpendCommand(
                            Action.APPLY_ACTUAL, n.getId(), null, null, null, null, null, null));
            amount(s.snapshot().getGoals().get(0).getActual(), "530000", "first actual saving");
            expect(
                    ALREADY_APPLIED,
                    () ->
                            s.manageNoSpend(
                                    new NoSpendCommand(
                                            Action.APPLY_ACTUAL,
                                            n.getId(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null)),
                    "duplicate saving rejected");
            amount(
                    s.manageGoal(new GoalCommand(Action.QUERY, null, null, null, null))
                            .get(0)
                            .getActualRate(),
                    "26.5",
                    "actual rate");
            amount(
                    s.manageGoal(new GoalCommand(Action.QUERY, null, null, null, null))
                            .get(0)
                            .getVirtualRate(),
                    "1.5",
                    "virtual remains separate");

            NoSpend without =
                    s.manageNoSpend(
                                    new NoSpendCommand(
                                            Action.REGISTER,
                                            null,
                                            "커피",
                                            b("5000"),
                                            LocalDate.of(2026, 10, 15),
                                            null,
                                            null,
                                            null))
                            .get(0);
            expect(
                    GOAL_REQUIRED,
                    () ->
                            s.manageNoSpend(
                                    new NoSpendCommand(
                                            Action.APPLY_ACTUAL,
                                            without.getId(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null)),
                    "target required");

            s.manageNoSpend(
                    new NoSpendCommand(
                            Action.LINK_GOAL,
                            without.getId(),
                            null,
                            null,
                            null,
                            g.getId(),
                            null,
                            null));
            Thread t1 = new Thread(() -> applyIgnoringDuplicate(s, without.getId()));
            Thread t2 = new Thread(() -> applyIgnoringDuplicate(s, without.getId()));

            t1.start();
            t2.start();
            t1.join();
            t2.join();
            amount(s.snapshot().getGoals().get(0).getActual(), "535000", "concurrent saving once");

            s.manageGoal(
                    new GoalCommand(Action.UPDATE, g.getId(), "여행 수정", b("500000"), b("535000")));
            amount(
                    s.manageGoal(new GoalCommand(Action.QUERY, null, null, null, null))
                            .get(0)
                            .getRemaining(),
                    "0",
                    "completed goal remaining zero");

            s.manageGoal(new GoalCommand(Action.DELETE, g.getId(), null, null, null));
            eq(s.snapshot().getGoals().isEmpty(), true, "goal deleted");
            eq(
                    s.snapshot().getNoSpends().stream().allMatch(x -> x.getGoalId() == null),
                    true,
                    "unlink records");
            eq(
                    s.snapshot().getNoSpends().stream().allMatch(NoSpend::getApplied),
                    true,
                    "applied history retained");
            expect(
                    ALREADY_APPLIED,
                    () ->
                            s.manageNoSpend(
                                    new NoSpendCommand(
                                            Action.LINK_GOAL,
                                            n.getId(),
                                            null,
                                            null,
                                            null,
                                            UUID.randomUUID(),
                                            null,
                                            null)),
                    "applied cannot relink");

            expect(
                    DATA_IN_USE,
                    () -> {
                        try (DataRepository ignored = new DataRepository(file)) {}
                    },
                    "second instance lock");
        }

        try (DataRepository repo = new DataRepository(file)) {
            AccountService s = new AccountService(repo, CLOCK);
            eq(s.snapshot().getGoals().isEmpty(), true, "goal deletion reload");
            eq(s.snapshot().getNoSpends().size(), 2, "records reload");
            eq(
                    s.snapshot().getNoSpends().stream().allMatch(NoSpend::getApplied),
                    true,
                    "applied reload");
        }

        Path failFile = folder.resolve("failure.dat");
        try (DataRepository writer = new DataRepository(failFile)) {
            writer.commit(DemoData.create());
        }
        byte[] before = Files.readAllBytes(failFile);

        try (DataRepository failing =
                new DataRepository(failFile) {
                    @Override
                    public void commit(State state) {
                        throw new AppException(IO_ERROR, "injected write failure");
                    }
                }) {
            AccountService s = new AccountService(failing, CLOCK);
            NoSpend n = s.snapshot().getNoSpends().get(0);
            expect(
                    IO_ERROR,
                    () ->
                            s.manageNoSpend(
                                    new NoSpendCommand(
                                            Action.APPLY_ACTUAL,
                                            n.getId(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null)),
                    "write failure reported");
            amount(
                    s.snapshot().getGoals().get(0).getActual(),
                    "500000",
                    "failure rolls back actual");
            eq(s.snapshot().getNoSpends().get(0).getApplied(), false, "failure rolls back applied");
        }
        eq(
                Arrays.equals(before, Files.readAllBytes(failFile)),
                true,
                "failure preserves file bytes");

        Path corrupt = folder.resolve("corrupt.dat");
        Files.writeString(corrupt, "not a serialized ledger");
        try (DataRepository repo = new DataRepository(corrupt)) {
            expect(IO_ERROR, () -> new AccountService(repo, CLOCK), "corrupt file refused");
        }
        eq(Files.readString(corrupt), "not a serialized ledger", "corrupt file not overwritten");

        Path sparse = folder.resolve("sparse.dat");
        try (DataRepository repo = new DataRepository(sparse)) {
            repo.commit(
                    new State(
                            List.of(
                                    new Entry(
                                            UUID.randomUUID(),
                                            LocalDate.of(2026, 10, 1),
                                            Type.EXPENSE,
                                            "식비",
                                            b("10000"),
                                            "new")),
                            Map.of(),
                            List.of(),
                            List.of(),
                            YearMonth.of(2026, 8)));
            AccountService s = new AccountService(repo, CLOCK);
            Analysis a = s.analyzeSpending(YearMonth.of(2026, 10), null, null);
            eq(a.getBaselineMonths(), 2, "sparse baseline count");
            amount(a.getCategories().get(0).getAverage(), "0", "zero-spend months included");
            eq(a.getCategories().get(0).getRate(), null, "zero baseline no rate");

            eq(
                    s.analyzeSpending(YearMonth.of(2026, 7), null, null).getEmpty(),
                    true,
                    "empty month analysis");
        }

        System.out.println("PASS: " + checks + " checks; data at " + folder.toAbsolutePath());
    }

    private static void applyIgnoringDuplicate(AccountService s, UUID id) {
        try {
            s.manageNoSpend(
                    new NoSpendCommand(
                            Action.APPLY_ACTUAL, id, null, null, null, null, null, null));
        } catch (AppException e) {
            if (e.code() != ALREADY_APPLIED) {
                throw e;
            }
        }
    }

    private static BigDecimal b(String s) {
        return new BigDecimal(s);
    }

    private static void amount(BigDecimal actual, String expected, String label) {
        eq(actual.compareTo(b(expected)), 0, label);
    }

    private static void eq(Object actual, Object expected, String label) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }

        checks++;
        System.out.println("OK " + checks + " " + label);
    }

    private static void expect(AppException.Code code, Runnable work, String label) {
        try {
            work.run();
            throw new AssertionError(label + ": no exception");
        } catch (AppException e) {
            eq(e.code(), code, label);
        }
    }
}
