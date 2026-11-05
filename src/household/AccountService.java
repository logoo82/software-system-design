package household;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static household.Domain.*;
import static household.Commands.*;
import static household.AppException.Code.*;

/**
 * 거래, 예산, 분석, 목표, 꾹참기 기능을 처리한다.
 * 입력을 확인하고 필요한 값을 계산한 뒤, 변경한 내용이 있으면 저장한다.
 * <p>가상 절약액은 실제 수입이나 저축액과 따로 계산한다.</p>
 * @author 김승혁
 * @version 0.4
 * @see DataRepository
 */
public final class AccountService {
    /** 거래를 입력할 때 고를 수 있는 카테고리. */
    public static final List<String> CATEGORIES =
            List.of("식비", "교통비", "쇼핑", "주거비", "문화생활", "급여", "용돈", "기타");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DataRepository repository;
    private final Clock clock;
    private State state;

    /**
     * 날짜 기준을 정하고 전에 저장한 내용을 불러온다.
     * @param repository 데이터를 읽고 저장할 곳
     * @param clock 계산에 사용할 날짜 기준
     * @throws AppException 저장 파일을 읽지 못하거나 내용에 문제가 있는 경우
     */
    public AccountService(DataRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);

        state = repository.load(YearMonth.from(today()));
        validateLoadedState();
    }

    /**
     * 거래를 추가, 수정, 삭제하거나 조건에 맞춰 조회한다.
     * @param c 할 작업과 입력값. 조회 조건을 비워두면 전체를 찾는다.
     * @return 최근 날짜부터 정리한 거래 목록
     * @throws AppException 입력이 잘못됐거나 선택한 항목이 없거나 저장에 실패한 경우
     */
    public synchronized List<Entry> manageTransaction(TransactionCommand c) {
        require(c != null && c.getAction() != null, "거래 작업을 선택해야 함.");

        // 조회할 때는 입력한 조건에 맞는 거래만 골라낸다.
        if (c.getAction() == Action.QUERY) {
            range(c.getFrom(), c.getTo());
            return state.getEntries().stream()
                    .filter(e -> c.getFrom() == null || !e.getDate().isBefore(c.getFrom()))
                    .filter(e -> c.getTo() == null || !e.getDate().isAfter(c.getTo()))
                    .filter(e -> c.getType() == null || e.getType() == c.getType())
                    .filter(e -> blank(c.getCategory()) || e.getCategory().equals(c.getCategory()))
                    .filter(e ->
                            blank(c.getKeyword())
                                    || e.getDescription()
                                            .toLowerCase(Locale.ROOT)
                                            .contains(
                                                    c.getKeyword().trim().toLowerCase(Locale.ROOT)))
                    .sorted(
                            Comparator.comparing(Entry::getDate)
                                    .reversed()
                                    .thenComparing(e -> e.getId().toString()))
                    .toList();
        }

        require(
                Set.of(Action.CREATE, Action.UPDATE, Action.DELETE).contains(c.getAction()),
                "지원하지 않는 거래 작업임.");
        // 추가, 수정, 삭제는 기존 목록을 복사해서 처리한다.
        List<Entry> entries = new ArrayList<>(state.getEntries());
        if (c.getAction() != Action.CREATE) {
            entry(c.getId());
        }

        if (c.getAction() == Action.DELETE) {
            entries.removeIf(e -> e.getId().equals(c.getId()));
        } else {
            date(c.getDate());
            require(c.getType() != null, "거래 유형을 선택해야 함.");
            require(CATEGORIES.contains(c.getCategory()), "카테고리를 선택해야 함.");
            money(c.getAmount(), false);

            String description = c.getDescription() == null ? "" : c.getDescription().trim();
            require(description.length() <= 200, "상세 내용은 200자까지 입력 가능함.");

            UUID id = c.getAction() == Action.CREATE ? UUID.randomUUID() : c.getId();
            entries.removeIf(e -> e.getId().equals(id));
            entries.add(
                    new Entry(
                            id,
                            c.getDate(),
                            c.getType(),
                            c.getCategory(),
                            c.getAmount(),
                            description));
        }

        YearMonth start =
                c.getAction() == Action.DELETE
                        ? state.getStartMonth()
                        : earlier(state.getStartMonth(), YearMonth.from(c.getDate()));
        // 입력 확인이 끝나면 저장하고, 화면에 보여줄 목록을 돌려준다.
        save(new State(entries, state.getBudgets(), state.getGoals(), state.getNoSpends(), start));

        return state.getEntries().stream()
                .sorted(Comparator.comparing(Entry::getDate).reversed())
                .toList();
    }

    /**
     * 월 예산을 저장하거나 선택한 달의 수입과 지출을 확인한다.
     * @param c 조회할 달과 작업. 예산을 저장할 때는 0원보다 큰 금액을 넣는다.
     * @return 월별 금액과 예산 현황. 미래의 달은 예산만 보여준다.
     * @throws AppException 월이나 예산 입력이 잘못됐거나 저장에 실패한 경우
     */
    public synchronized Summary manageMonthlyFinance(BudgetCommand c) {
        require(c != null && c.getMonth() != null, "조회할 월을 선택해야 함.");
        require(
                c.getAction() == Action.QUERY || c.getAction() == Action.SET_BUDGET,
                "지원하지 않는 예산 작업임.");

        // 예산을 바꿀 때만 저장하고, 조회할 때는 계산만 한다.
        if (c.getAction() == Action.SET_BUDGET) {
            money(c.getBudget(), false);
            Map<YearMonth, BigDecimal> budgets = new HashMap<>(state.getBudgets());
            budgets.put(c.getMonth(), c.getBudget());
            save(
                    new State(
                            state.getEntries(),
                            budgets,
                            state.getGoals(),
                            state.getNoSpends(),
                            state.getStartMonth()));
        }

        BigDecimal budget = state.getBudgets().get(c.getMonth());
        if (c.getMonth().isAfter(YearMonth.from(today()))) {
            return new Summary(null, null, null, budget, null, null, null, null);
        }
        BigDecimal income = sum(c.getMonth(), Type.INCOME);
        BigDecimal expense = sum(c.getMonth(), Type.EXPENSE);
        BigDecimal forecast = forecast(c.getMonth(), expense);

        return new Summary(
                income,
                expense,
                income.subtract(expense),
                budget,
                budget == null ? null : budget.subtract(expense),
                budget == null ? null : percent(expense, budget),
                forecast,
                budget == null ? null : forecast.subtract(budget).max(ZERO));
    }

    /**
     * 선택한 달에 어디에 얼마나 썼는지 확인하고, 줄일 수 있는 금액을 계산한다.
     * 이번 달은 아직 한 달이 끝나지 않았으므로 이전 달들의 평균과 비교할 때 차이가 있다.
     * @param month 확인할 달
     * @param category 줄여볼 카테고리. 비율과 함께 비워두면 소비 내역만 분석한다.
     * @param rate 줄일 비율. 20%는 0.20으로 넣으며 0보다 크고 1 이하여야 한다.
     * @return 소비 분석 결과와 비교한 개월 수, 예상 지출, 절감액
     * @throws AppException 미래의 달을 골랐거나 카테고리와 비율이 잘못된 경우
     */
    public synchronized Analysis analyzeSpending(
            YearMonth month, String category, BigDecimal rate) {
        require(month != null, "분석할 월을 선택해야 함.");
        if (month.isAfter(YearMonth.from(today()))) {
            throw new AppException(INVALID_MONTH, "미래 월은 분석할 수 없음.");
        }
        require((category == null) == (rate == null), "카테고리와 절감 비율을 함께 선택해야 함.");
        if (rate != null) {
            require(
                    CATEGORIES.contains(category)
                            && rate.signum() > 0
                            && rate.compareTo(BigDecimal.ONE) <= 0,
                    "절감 비율은 1~100%만 가능함.");
        }

        BigDecimal total = sum(month, Type.EXPENSE);

        // 선택한 달 바로 전 3개월을 비교 대상으로 잡는다.
        List<YearMonth> baseline = new ArrayList<>();
        for (int i = 3; i >= 1; i--) {
            if (!month.minusMonths(i).isBefore(state.getStartMonth())) {
                baseline.add(month.minusMonths(i));
            }
        }

        Map<String, BigDecimal> current = expenseByCategory(month);
        Set<String> names = new TreeSet<>(current.keySet());
        baseline.forEach(m -> names.addAll(expenseByCategory(m).keySet()));

        List<CategoryStat> stats = new ArrayList<>();
        for (String name : names) {
            BigDecimal spent = current.getOrDefault(name, ZERO);
            BigDecimal average = null;

            // 비교할 달에 지출이 없었어도 0원으로 넣어서 평균을 낸다.
            if (!baseline.isEmpty()) {
                BigDecimal past =
                        baseline.stream()
                                .map(m -> expenseByCategory(m).getOrDefault(name, ZERO))
                                .reduce(ZERO, BigDecimal::add);
                average = past.divide(BigDecimal.valueOf(baseline.size()), 6, RoundingMode.HALF_UP);
            }

            BigDecimal delta = average == null ? null : spent.subtract(average);
            stats.add(
                    new CategoryStat(
                            name,
                            spent,
                            total.signum() == 0 ? ZERO : percent(spent, total),
                            average,
                            delta,
                            average == null || average.signum() == 0
                                    ? null
                                    : percent(delta, average)));
        }

        BigDecimal saving =
                rate == null ? null : current.getOrDefault(category, ZERO).multiply(rate);

        return new Analysis(
                List.copyOf(stats),
                baseline.size(),
                forecast(month, total),
                saving,
                saving == null ? null : saving.multiply(BigDecimal.valueOf(12)),
                total.signum() == 0);
    }

    /**
     * 목표를 관리하고 실제로 저축한 금액을 더한다.
     * 수정할 때는 저축 총액을 바꾸고, 저축액 추가에서는 새로 모은 금액만 더한다.
     * 목표를 지워도 꾹참기 기록과 이미 반영했다는 표시는 남겨둔다.
     * @param c 목표에서 할 작업과 입력값
     * @return 목표별 실제 저축액과 가상 절약액, 달성률
     * @throws AppException 입력이 잘못됐거나 선택한 항목이 없거나 저장에 실패한 경우
     */
    public synchronized List<GoalStatus> manageGoal(GoalCommand c) {
        require(c != null && c.getAction() != null, "목표 작업을 선택해야 함.");

        if (c.getAction() != Action.QUERY) {
            require(
                    Set.of(Action.CREATE, Action.UPDATE, Action.DELETE, Action.ADD_SAVING)
                            .contains(c.getAction()),
                    "지원하지 않는 목표 작업임.");
            List<Goal> goals = new ArrayList<>(state.getGoals());
            List<NoSpend> records = new ArrayList<>(state.getNoSpends());
            Goal old = c.getAction() == Action.CREATE ? null : goal(c.getId());

            // 목표만 지우고 꾹참기 기록과 이미 반영했다는 표시는 남겨둔다.
            if (c.getAction() == Action.DELETE) {
                goals.removeIf(g -> g.getId().equals(c.getId()));
                records.replaceAll(n ->
                        Objects.equals(n.getGoalId(), c.getId())
                                ? new NoSpend(
                                        n.getId(),
                                        n.getItem(),
                                        n.getAmount(),
                                        n.getDate(),
                                        null,
                                        n.getApplied())
                                : n);
            } else {
                Goal next;
                if (c.getAction() == Action.ADD_SAVING) {
                    money(c.getActual(), false);
                    next =
                            new Goal(
                                    old.getId(),
                                    old.getName(),
                                    old.getTarget(),
                                    old.getActual().add(c.getActual()));
                } else {
                    String name = name(c.getName());
                    money(c.getTarget(), false);
                    money(c.getActual(), true);
                    next =
                            new Goal(
                                    old == null ? UUID.randomUUID() : old.getId(),
                                    name,
                                    c.getTarget(),
                                    c.getActual());
                }

                goals.removeIf(g -> g.getId().equals(next.getId()));
                goals.add(next);
            }

            save(
                    new State(
                            state.getEntries(),
                            state.getBudgets(),
                            goals,
                            records,
                            state.getStartMonth()));
        }

        return state.getGoals().stream().map(this::status).toList();
    }

    /**
     * 참은 소비를 기록하고, 실제로 저축했다면 목표에 반영한다.
     * 기록만 했을 때는 실제 저축액이 늘지 않는다. 실제 반영은 한 건당 전액을 한 번만 한다.
     * @param c 할 작업과 기록 내용, 조회할 때 사용할 날짜 조건
     * @return 처리한 기록 한 건 또는 조회한 목록
     * @throws AppException 입력이나 저장에 문제가 있거나 목표가 없거나 이미 반영한 경우
     */
    public synchronized List<NoSpend> manageNoSpend(NoSpendCommand c) {
        require(c != null && c.getAction() != null, "꾹참기 작업을 선택해야 함.");

        if (c.getAction() == Action.QUERY) {
            range(c.getFrom(), c.getTo());
            return state.getNoSpends().stream()
                    .filter(n -> c.getFrom() == null || !n.getDate().isBefore(c.getFrom()))
                    .filter(n -> c.getTo() == null || !n.getDate().isAfter(c.getTo()))
                    .sorted(Comparator.comparing(NoSpend::getDate).reversed())
                    .toList();
        }

        List<NoSpend> records = new ArrayList<>(state.getNoSpends());
        List<Goal> goals = new ArrayList<>(state.getGoals());
        NoSpend next;

        // 처음에는 가상 기록만 만들고 실제 저축액은 그대로 둔다.
        if (c.getAction() == Action.REGISTER) {
            String item = name(c.getItem());
            money(c.getAmount(), false);
            date(c.getDate());
            if (c.getGoalId() != null) {
                goal(c.getGoalId());
            }

            next =
                    new NoSpend(
                            UUID.randomUUID(),
                            item,
                            c.getAmount(),
                            c.getDate(),
                            c.getGoalId(),
                            false);
        } else {
            NoSpend old = noSpend(c.getId());
            // 이미 반영했다면 여기서 멈춰서 두 번 더해지지 않게 한다.
            if (old.getApplied()) {
                throw new AppException(ALREADY_APPLIED, "이미 실제 저축에 반영됨.");
            }

            if (c.getAction() == Action.LINK_GOAL) {
                require(old.getGoalId() == null, "이미 연결된 목표가 있음.");
                goal(c.getGoalId());
                next =
                        new NoSpend(
                                old.getId(),
                                old.getItem(),
                                old.getAmount(),
                                old.getDate(),
                                c.getGoalId(),
                                false);
            } else if (c.getAction() == Action.APPLY_ACTUAL) {
                if (old.getGoalId() == null) {
                    throw new AppException(GOAL_REQUIRED, "목표 연결이 필요함.");
                }
                // 목표에 금액을 더하고 반영 여부도 같이 바꿔준다.
                Goal g = goal(old.getGoalId());
                goals.replaceAll(x ->
                        x.getId().equals(g.getId())
                                ? new Goal(
                                        g.getId(),
                                        g.getName(),
                                        g.getTarget(),
                                        g.getActual().add(old.getAmount()))
                                : x);
                next =
                        new NoSpend(
                                old.getId(),
                                old.getItem(),
                                old.getAmount(),
                                old.getDate(),
                                old.getGoalId(),
                                true);
            } else {
                throw new AppException(VALIDATION_ERROR, "지원하지 않는 꾹참기 작업임.");
            }

            records.removeIf(n -> n.getId().equals(old.getId()));
        }

        // 바뀐 기록과 목표 금액을 한 번에 저장한다.
        records.add(next);
        save(
                new State(
                        state.getEntries(),
                        state.getBudgets(),
                        goals,
                        records,
                        state.getStartMonth()));

        return List.of(next);
    }

    /**
     * 선택한 꾹참기 기록으로 영수증 내용을 만든다. 누적액과 목표 정보는 지금 기준이다.
     * @param id 기록 ID
     * @return 목표 정보와 실제 반영 여부를 포함한 영수증
     * @throws AppException 기록이 없음
     */
    public synchronized String receipt(UUID id) {
        NoSpend n = noSpend(id);
        BigDecimal cumulative =
                state.getNoSpends().stream().map(NoSpend::getAmount).reduce(ZERO, BigDecimal::add);

        StringBuilder b =
                new StringBuilder("꾹참기 대체 소비 영수증\n────────────────────\n")
                        .append("날짜: ")
                        .append(n.getDate())
                        .append("\n참은 소비: ")
                        .append(n.getItem())
                        .append("\n예정 금액: ")
                        .append(won(n.getAmount()))
                        .append("원\n누적 가상 절약액 (전체): ")
                        .append(won(cumulative))
                        .append("원\n");

        if (n.getGoalId() != null) {
            Goal g = goal(n.getGoalId());
            GoalStatus gs = status(g);
            b.append("연결 목표: ")
                    .append(g.getName())
                    .append("\n이번 금액의 목표 대비 비율: ")
                    .append(one(percent(n.getAmount(), g.getTarget())))
                    .append("%")
                    .append("\n가상 목표 진척도: ")
                    .append(one(gs.getVirtualRate()))
                    .append("%")
                    .append("\n실제 저축액: ")
                    .append(won(g.getActual()))
                    .append("원 / 실제 달성률: ")
                    .append(one(gs.getActualRate()))
                    .append("%\n");
        } else {
            b.append("연결 목표: 없음\n");
        }

        return b.append("실제 저축 반영: ")
                .append(n.getApplied() ? "완료" : "미반영")
                .toString();
    }

    /**
     * 화면에서 확인할 수 있도록 현재 저장 내용을 돌려준다.
     * @return 현재 거래, 예산, 목표와 꾹참기 내용
     */
    public synchronized State snapshot() {
        return state;
    }

    /**
     * 지금 계산에 쓰고 있는 날짜를 확인한다.
     * @return 일반 실행은 오늘, 데모는 정해둔 날짜
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * 데이터가 저장되는 위치를 확인한다.
     * @return 저장 파일의 위치
     */
    public String dataPath() {
        return repository.path().toString();
    }

    /**
     * 금액을 정수 원으로 표시한다.
     * @param n 금액, null 허용
     * @return 쉼표 포함 문자열 또는 미설정
     */
    public static String won(BigDecimal n) {
        return n == null
                ? "미설정"
                : String.format(Locale.KOREA, "%,.0f", n.setScale(0, RoundingMode.HALF_UP));
    }

    /**
     * 비율을 소수 한 자리로 표시한다.
     * @param n 비율
     * @return 표시 문자열
     */
    public static String one(BigDecimal n) {
        return n == null ? "-" : n.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    // 파일 저장이 끝나야 프로그램 안의 데이터도 바꾼다.
    private void save(State next) {
        repository.commit(next);
        state = next;
    }

    private GoalStatus status(Goal g) {
        BigDecimal virtual =
                state.getNoSpends().stream()
                        .filter(n -> Objects.equals(n.getGoalId(), g.getId()))
                        .map(NoSpend::getAmount)
                        .reduce(ZERO, BigDecimal::add);
        return new GoalStatus(
                g,
                virtual,
                percent(g.getActual(), g.getTarget()),
                percent(virtual, g.getTarget()),
                g.getTarget().subtract(g.getActual()).max(ZERO));
    }

    private BigDecimal sum(YearMonth month, Type type) {
        return state.getEntries().stream()
                .filter(e ->
                        YearMonth.from(e.getDate()).equals(month)
                                && e.getType() == type
                                && !e.getDate().isAfter(today()))
                .map(Entry::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> expenseByCategory(YearMonth month) {
        return state.getEntries().stream()
                .filter(e ->
                        e.getType() == Type.EXPENSE
                                && YearMonth.from(e.getDate()).equals(month)
                                && !e.getDate().isAfter(today()))
                .collect(Collectors.toMap(Entry::getCategory, Entry::getAmount, BigDecimal::add));
    }

    private BigDecimal forecast(YearMonth month, BigDecimal spent) {
        return month.equals(YearMonth.from(today()))
                ? spent.multiply(BigDecimal.valueOf(month.lengthOfMonth()))
                        .divide(
                                BigDecimal.valueOf(today().getDayOfMonth()),
                                6,
                                RoundingMode.HALF_UP)
                : spent;
    }

    private static BigDecimal percent(BigDecimal a, BigDecimal b) {
        return a.multiply(HUNDRED).divide(b, 6, RoundingMode.HALF_UP);
    }

    private Entry entry(UUID id) {
        return state.getEntries().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(NOT_FOUND, "거래를 다시 선택해야 함."));
    }

    private Goal goal(UUID id) {
        return state.getGoals().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(NOT_FOUND, "목표를 다시 선택해야 함."));
    }

    private NoSpend noSpend(UUID id) {
        return state.getNoSpends().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(NOT_FOUND, "꾹참기 내역을 다시 선택해야 함."));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static YearMonth earlier(YearMonth a, YearMonth b) {
        return a.isBefore(b) ? a : b;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new AppException(VALIDATION_ERROR, message);
        }
    }

    private static String name(String s) {
        require(s != null && !s.isBlank() && s.trim().length() <= 50, "이름은 1~50자만 가능함.");
        return s.trim();
    }

    private void date(LocalDate date) {
        require(date != null && !date.isAfter(today()), "오늘까지의 날짜만 입력 가능함.");
    }

    private static void range(LocalDate a, LocalDate b) {
        require(a == null || b == null || !a.isAfter(b), "종료일은 시작일보다 빠를 수 없음.");
    }

    private static void money(BigDecimal v, boolean zero) {
        require(
                v != null
                        && (zero ? v.signum() >= 0 : v.signum() > 0)
                        && v.stripTrailingZeros().scale() <= 0
                        && v.compareTo(new BigDecimal("999999999999")) <= 0,
                (zero ? "0 이상" : "0 초과") + " 정수만 가능함. 최대 999,999,999,999원.");
    }

    // 불러온 파일에도 필요한 값이 빠진 곳이 없는지 확인한다.
    private void validateLoadedState() {
        try {
            Set<UUID> ids = new HashSet<>();
            for (Entry e : state.getEntries()) {
                require(e.getId() != null && ids.add(e.getId()), "거래 ID 오류");
                require(
                        e.getDate() != null
                                && e.getType() != null
                                && CATEGORIES.contains(e.getCategory())
                                && e.getDescription() != null
                                && e.getDescription().length() <= 200,
                        "거래 형식 오류");
                money(e.getAmount(), false);
            }

            ids.clear();
            for (Goal g : state.getGoals()) {
                require(g.getId() != null && ids.add(g.getId()), "목표 ID 오류");
                name(g.getName());
                money(g.getTarget(), false);
                money(g.getActual(), true);
            }

            ids.clear();
            for (NoSpend n : state.getNoSpends()) {
                require(
                        n.getId() != null && ids.add(n.getId()) && n.getDate() != null,
                        "꾹참기 ID 오류");
                name(n.getItem());
                money(n.getAmount(), false);
                if (n.getGoalId() != null) {
                    goal(n.getGoalId());
                }
            }

            state.getBudgets()
                    .forEach((k, v) -> {
                        require(k != null, "예산 월 오류");
                        money(v, false);
                    });
        } catch (RuntimeException e) {
            throw new AppException(IO_ERROR, "저장 파일의 내용이 올바르지 않음: " + e.getMessage());
        }
    }
}
