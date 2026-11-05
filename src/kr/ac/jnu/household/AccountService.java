package kr.ac.jnu.household;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static kr.ac.jnu.household.Domain.*;
import static kr.ac.jnu.household.Commands.*;
import static kr.ac.jnu.household.AppException.Code.*;

/**
 * CP3 FS-01~05를 구현하는 업무 계층. 다섯 대표 함수는 동기화되어 변경의
 * 검사·저장 순서를 보장한다. 파일 저장 성공 후에만 현재 상태를 교체한다.
 * <p>계산은 선택 월의 실제 거래만 사용하고, 가상 절약은 거래 합계에 더하지 않는다.</p>
 * @author 김승혁
 * @version 0.4
 * @see DataRepository
 */
public final class AccountService {
    /** 화면과 분석에서 사용하는 기본 카테고리. */
    public static final List<String> CATEGORIES =
            List.of("식비", "교통비", "쇼핑", "주거비", "문화생활", "급여", "용돈", "기타");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DataRepository repository;
    private final Clock clock;
    private State state;

    /**
     * 저장 상태와 날짜 기준을 준비한다.
     * @param repository 파일 저장소
     * @param clock 날짜 기준 (데모·테스트는 고정 가능)
     * @throws AppException 파일 읽기 또는 상태 검증 실패
     */
    public AccountService(DataRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);

        state = repository.load(YearMonth.from(today()));
        validateLoadedState();
    }

    /**
     * FS-01 거래 생성·수정·삭제·AND 검색을 처리한다.
     * @param c 거래 명령. QUERY의 생략한 조건은 전체를 뜻한다.
     * @return 최신 거래 또는 검색 결과, 날짜 내림차순
     * @throws AppException 입력값 오류, ID 없음, 저장 실패
     */
    public synchronized List<Entry> manageTransaction(TransactionCommand c) {
        require(c != null && c.getAction() != null, "거래 작업을 선택해 주세요.");

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
                "허용하지 않은 거래 작업입니다.");
        List<Entry> entries = new ArrayList<>(state.getEntries());
        if (c.getAction() != Action.CREATE) {
            entry(c.getId());
        }

        if (c.getAction() == Action.DELETE) {
            entries.removeIf(e -> e.getId().equals(c.getId()));
        } else {
            date(c.getDate());
            require(c.getType() != null, "거래 유형을 선택해 주세요.");
            require(CATEGORIES.contains(c.getCategory()), "카테고리를 선택해 주세요.");
            money(c.getAmount(), false);

            String description = c.getDescription() == null ? "" : c.getDescription().trim();
            require(description.length() <= 200, "상세 내용은 200자 이하로 입력해 주세요.");

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
        save(new State(entries, state.getBudgets(), state.getGoals(), state.getNoSpends(), start));

        return state.getEntries().stream()
                .sorted(Comparator.comparing(Entry::getDate).reversed())
                .toList();
    }

    /**
     * FS-02 예산을 설정하거나 선택 월의 현황을 조회한다.
     * @param c QUERY 또는 SET_BUDGET, 연월, 설정 시 양수 예산
     * @return 수입·지출·잔액·예산 지표·예상 지출. 미래 월은 예산만 제공한다.
     * @throws AppException 잘못된 월·예산, 저장 실패
     */
    public synchronized Summary manageMonthlyFinance(BudgetCommand c) {
        require(c != null && c.getMonth() != null, "조회 월을 선택해 주세요.");
        require(
                c.getAction() == Action.QUERY || c.getAction() == Action.SET_BUDGET,
                "허용하지 않은 예산 작업입니다.");

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
     * FS-03 현재·과거 월의 카테고리별 지출과 직전 3개월 평균을 비교한다.
     * 현재 월은 오늘까지 누적액이며 과거 월 전체 평균과 비교한다는 한계가 있다.
     * @param month 분석 월
     * @param category 절감 대상. rate와 함께 null이면 분석만 수행한다.
     * @param rate 0 초과 1 이하의 절감률 (20%는 0.20)
     * @return 통계·비교 개월 수·예측·선택적 절감액
     * @throws AppException 미래 월, 비율·카테고리 오류
     */
    public synchronized Analysis analyzeSpending(
            YearMonth month, String category, BigDecimal rate) {
        require(month != null, "분석 월을 선택해 주세요.");
        if (month.isAfter(YearMonth.from(today()))) {
            throw new AppException(INVALID_MONTH, "미래 월은 분석할 수 없습니다.");
        }
        require((category == null) == (rate == null), "절감 카테고리와 비율을 함께 선택해 주세요.");
        if (rate != null) {
            require(
                    CATEGORIES.contains(category)
                            && rate.signum() > 0
                            && rate.compareTo(BigDecimal.ONE) <= 0,
                    "절감 비율은 1~100% 범위로 선택해 주세요.");
        }

        BigDecimal total = sum(month, Type.EXPENSE);

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
     * FS-04 목표 관리. UPDATE의 actual은 새 총액, ADD_SAVING은 추가액이다.
     * 삭제 시 꾹참기 연결만 해제하고 반영 상태를 유지한다.
     * @param c 목표 작업과 입력
     * @return 전체 목표별 실제·가상 현황
     * @throws AppException 입력값 오류, ID 없음, 저장 실패
     */
    public synchronized List<GoalStatus> manageGoal(GoalCommand c) {
        require(c != null && c.getAction() != null, "목표 작업을 선택해 주세요.");

        if (c.getAction() != Action.QUERY) {
            require(
                    Set.of(Action.CREATE, Action.UPDATE, Action.DELETE, Action.ADD_SAVING)
                            .contains(c.getAction()),
                    "허용하지 않은 목표 작업입니다.");
            List<Goal> goals = new ArrayList<>(state.getGoals());
            List<NoSpend> records = new ArrayList<>(state.getNoSpends());
            Goal old = c.getAction() == Action.CREATE ? null : goal(c.getId());

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
     * FS-05 꾹참기 등록·검색·목표 연결·실제 저축 반영을 처리한다.
     * APPLY_ACTUAL은 저장된 금액 전액을 한 번만 반영한다.
     * @param c 작업과 기록 ID, 등록값 또는 날짜 조회 조건
     * @return 처리 기록 또는 검색 결과
     * @throws AppException 입력 오류, 없는 ID, 목표 미연결, 중복 반영, 저장 실패
     */
    public synchronized List<NoSpend> manageNoSpend(NoSpendCommand c) {
        require(c != null && c.getAction() != null, "꾹참기 작업을 선택해 주세요.");

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
            if (old.getApplied()) {
                throw new AppException(ALREADY_APPLIED, "이미 실제 저축에 반영한 내역입니다.");
            }

            if (c.getAction() == Action.LINK_GOAL) {
                require(old.getGoalId() == null, "이미 목표가 연결되어 있습니다.");
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
                    throw new AppException(GOAL_REQUIRED, "목표를 먼저 연결해 주세요.");
                }
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
                throw new AppException(VALIDATION_ERROR, "허용하지 않은 꾹참기 작업입니다.");
            }

            records.removeIf(n -> n.getId().equals(old.getId()));
        }

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
     * 저장된 꾹참기 한 건을 가상 영수증으로 표시한다. 누적액은 현재 전체 이력 기준이다.
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
                .append("\n\n가상 절약액은 실제 저축액과 합산하지 않습니다.\n조회 시점의 목표 정보와 전체 누적액을 표시합니다.")
                .toString();
    }

    /**
     * 읽기 전용 전체 상태를 반환한다.
     * @return 불변 데이터
     */
    public synchronized State snapshot() {
        return state;
    }

    /**
     * 일관된 조회 기준일.
     * @return Clock의 현재 날짜
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * 데이터 파일 위치.
     * @return 경로 문자열
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
                .orElseThrow(() -> new AppException(NOT_FOUND, "거래를 다시 선택해 주세요."));
    }

    private Goal goal(UUID id) {
        return state.getGoals().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(NOT_FOUND, "목표를 다시 선택해 주세요."));
    }

    private NoSpend noSpend(UUID id) {
        return state.getNoSpends().stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AppException(NOT_FOUND, "꾹참기 내역을 다시 선택해 주세요."));
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
        require(s != null && !s.isBlank() && s.trim().length() <= 50, "이름은 1~50자로 입력해 주세요.");
        return s.trim();
    }

    private void date(LocalDate date) {
        require(date != null && !date.isAfter(today()), "날짜는 오늘 이전 또는 오늘로 입력해 주세요.");
    }

    private static void range(LocalDate a, LocalDate b) {
        require(a == null || b == null || !a.isAfter(b), "시작일은 종료일 이후일 수 없습니다.");
    }

    private static void money(BigDecimal v, boolean zero) {
        require(
                v != null
                        && (zero ? v.signum() >= 0 : v.signum() > 0)
                        && v.stripTrailingZeros().scale() <= 0
                        && v.compareTo(new BigDecimal("999999999999")) <= 0,
                (zero ? "0 이상" : "0 초과") + " 999,999,999,999원 이하의 정수 금액을 입력해 주세요.");
    }

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
            throw new AppException(IO_ERROR, "저장 데이터가 올바르지 않습니다. 원본은 보존됩니다: " + e.getMessage());
        }
    }
}
