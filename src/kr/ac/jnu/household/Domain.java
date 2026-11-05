package kr.ac.jnu.household;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** 저장 모델과 계산 결과를 정의하는 일반 데이터 클래스 모음.
 * @author 김승혁 223247
 * @version 0.4
 */
public final class Domain {
    private Domain() {}

    /** 거래 유형. */
    public enum Type {
        /** 수입. */
        INCOME,
        /** 지출. */
        EXPENSE
    }

    /** 수입 또는 지출 한 건. 생성 후 값은 변경하지 않는다. */
    public static final class Entry implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UUID id;
        private final LocalDate date;
        private final Type type;
        private final String category;
        private final BigDecimal amount;
        private final String description;

        /**
         * 수입 또는 지출 한 건 생성.
         * @param id 거래 ID
         * @param date 거래일
         * @param type 수입 또는 지출
         * @param category 카테고리
         * @param amount 정수 원 금액
         * @param description 상세 내용
         */
        public Entry(
                UUID id,
                LocalDate date,
                Type type,
                String category,
                BigDecimal amount,
                String description) {
            this.id = id;
            this.date = date;
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.description = description;
        }

        /**
         * 거래 ID.
         * @return 거래 ID
         */
        public UUID getId() {
            return id;
        }

        /**
         * 거래일.
         * @return 거래일
         */
        public LocalDate getDate() {
            return date;
        }

        /**
         * 수입 또는 지출.
         * @return 수입 또는 지출
         */
        public Type getType() {
            return type;
        }

        /**
         * 카테고리.
         * @return 카테고리
         */
        public String getCategory() {
            return category;
        }

        /**
         * 정수 원 금액.
         * @return 정수 원 금액
         */
        public BigDecimal getAmount() {
            return amount;
        }

        /**
         * 상세 내용.
         * @return 상세 내용
         */
        public String getDescription() {
            return description;
        }
    }

    /** 미래 목표. 생성 후 값은 변경하지 않는다. */
    public static final class Goal implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UUID id;
        private final String name;
        private final BigDecimal target;
        private final BigDecimal actual;

        /**
         * 미래 목표 생성.
         * @param id 목표 ID
         * @param name 목표 이름
         * @param target 목표 금액
         * @param actual 실제 저축액
         */
        public Goal(UUID id, String name, BigDecimal target, BigDecimal actual) {
            this.id = id;
            this.name = name;
            this.target = target;
            this.actual = actual;
        }

        /**
         * 목표 ID.
         * @return 목표 ID
         */
        public UUID getId() {
            return id;
        }

        /**
         * 목표 이름.
         * @return 목표 이름
         */
        public String getName() {
            return name;
        }

        /**
         * 목표 금액.
         * @return 목표 금액
         */
        public BigDecimal getTarget() {
            return target;
        }

        /**
         * 실제 저축액.
         * @return 실제 저축액
         */
        public BigDecimal getActual() {
            return actual;
        }
    }

    /** 참은 소비 기록. 생성 후 값은 변경하지 않는다. */
    public static final class NoSpend implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UUID id;
        private final String item;
        private final BigDecimal amount;
        private final LocalDate date;
        private final UUID goalId;
        private final boolean applied;

        /**
         * 참은 소비 기록 생성.
         * @param id 기록 ID
         * @param item 소비 이름
         * @param amount 예정 금액
         * @param date 기록일
         * @param goalId 연결 목표, 없으면 null
         * @param applied 실제 저축 반영 여부
         */
        public NoSpend(
                UUID id,
                String item,
                BigDecimal amount,
                LocalDate date,
                UUID goalId,
                boolean applied) {
            this.id = id;
            this.item = item;
            this.amount = amount;
            this.date = date;
            this.goalId = goalId;
            this.applied = applied;
        }

        /**
         * 기록 ID.
         * @return 기록 ID
         */
        public UUID getId() {
            return id;
        }

        /**
         * 소비 이름.
         * @return 소비 이름
         */
        public String getItem() {
            return item;
        }

        /**
         * 예정 금액.
         * @return 예정 금액
         */
        public BigDecimal getAmount() {
            return amount;
        }

        /**
         * 기록일.
         * @return 기록일
         */
        public LocalDate getDate() {
            return date;
        }

        /**
         * 연결 목표, 없으면 null.
         * @return 연결 목표, 없으면 null
         */
        public UUID getGoalId() {
            return goalId;
        }

        /**
         * 실제 저축 반영 여부.
         * @return 실제 저축 반영 여부
         */
        public boolean getApplied() {
            return applied;
        }
    }

    /** 전체 저장 데이터. 생성 후 값은 변경하지 않는다. */
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<Entry> entries;
        private final Map<YearMonth, BigDecimal> budgets;
        private final List<Goal> goals;
        private final List<NoSpend> noSpends;
        private final YearMonth startMonth;

        /**
         * 전체 저장 데이터 생성.
         * @param entries 거래 목록
         * @param budgets 월별 예산
         * @param goals 목표 목록
         * @param noSpends 꾹참기 목록
         * @param startMonth 기록 시작 월
         */
        public State(
                List<Entry> entries,
                Map<YearMonth, BigDecimal> budgets,
                List<Goal> goals,
                List<NoSpend> noSpends,
                YearMonth startMonth) {
            this.entries = List.copyOf(entries);
            this.budgets = Map.copyOf(budgets);
            this.goals = List.copyOf(goals);
            this.noSpends = List.copyOf(noSpends);
            this.startMonth = startMonth;
        }

        /**
         * 거래 목록.
         * @return 거래 목록
         */
        public List<Entry> getEntries() {
            return entries;
        }

        /**
         * 월별 예산.
         * @return 월별 예산
         */
        public Map<YearMonth, BigDecimal> getBudgets() {
            return budgets;
        }

        /**
         * 목표 목록.
         * @return 목표 목록
         */
        public List<Goal> getGoals() {
            return goals;
        }

        /**
         * 꾹참기 목록.
         * @return 꾹참기 목록
         */
        public List<NoSpend> getNoSpends() {
            return noSpends;
        }

        /**
         * 기록 시작 월.
         * @return 기록 시작 월
         */
        public YearMonth getStartMonth() {
            return startMonth;
        }

        /**
         * 신규 사용자의 빈 데이터.
         * @param month 기록 시작 월
         * @return 빈 상태
         */
        public static State empty(YearMonth month) {
            return new State(List.of(), Map.of(), List.of(), List.of(), month);
        }
    }

    /** 월별 재정 현황. 생성 후 값은 변경하지 않는다. */
    public static final class Summary implements Serializable {
        private static final long serialVersionUID = 1L;
        private final BigDecimal income;
        private final BigDecimal expense;
        private final BigDecimal balance;
        private final BigDecimal budget;
        private final BigDecimal remaining;
        private final BigDecimal usage;
        private final BigDecimal forecast;
        private final BigDecimal overrun;

        /**
         * 월별 재정 현황 생성.
         * @param income 수입
         * @param expense 지출
         * @param balance 월간 잔액
         * @param budget 예산, 없으면 null
         * @param remaining 남은 예산
         * @param usage 예산 사용률
         * @param forecast 예상 지출
         * @param overrun 예상 초과액
         */
        public Summary(
                BigDecimal income,
                BigDecimal expense,
                BigDecimal balance,
                BigDecimal budget,
                BigDecimal remaining,
                BigDecimal usage,
                BigDecimal forecast,
                BigDecimal overrun) {
            this.income = income;
            this.expense = expense;
            this.balance = balance;
            this.budget = budget;
            this.remaining = remaining;
            this.usage = usage;
            this.forecast = forecast;
            this.overrun = overrun;
        }

        /**
         * 수입.
         * @return 수입
         */
        public BigDecimal getIncome() {
            return income;
        }

        /**
         * 지출.
         * @return 지출
         */
        public BigDecimal getExpense() {
            return expense;
        }

        /**
         * 월간 잔액.
         * @return 월간 잔액
         */
        public BigDecimal getBalance() {
            return balance;
        }

        /**
         * 예산, 없으면 null.
         * @return 예산, 없으면 null
         */
        public BigDecimal getBudget() {
            return budget;
        }

        /**
         * 남은 예산.
         * @return 남은 예산
         */
        public BigDecimal getRemaining() {
            return remaining;
        }

        /**
         * 예산 사용률.
         * @return 예산 사용률
         */
        public BigDecimal getUsage() {
            return usage;
        }

        /**
         * 예상 지출.
         * @return 예상 지출
         */
        public BigDecimal getForecast() {
            return forecast;
        }

        /**
         * 예상 초과액.
         * @return 예상 초과액
         */
        public BigDecimal getOverrun() {
            return overrun;
        }
    }

    /** 카테고리별 분석값. 생성 후 값은 변경하지 않는다. */
    public static final class CategoryStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String category;
        private final BigDecimal spent;
        private final BigDecimal share;
        private final BigDecimal average;
        private final BigDecimal delta;
        private final BigDecimal rate;

        /**
         * 카테고리별 분석값 생성.
         * @param category 카테고리
         * @param spent 현재 지출
         * @param share 비율
         * @param average 직전 평균
         * @param delta 증감액
         * @param rate 증감률, 기준 0이면 null
         */
        public CategoryStat(
                String category,
                BigDecimal spent,
                BigDecimal share,
                BigDecimal average,
                BigDecimal delta,
                BigDecimal rate) {
            this.category = category;
            this.spent = spent;
            this.share = share;
            this.average = average;
            this.delta = delta;
            this.rate = rate;
        }

        /**
         * 카테고리.
         * @return 카테고리
         */
        public String getCategory() {
            return category;
        }

        /**
         * 현재 지출.
         * @return 현재 지출
         */
        public BigDecimal getSpent() {
            return spent;
        }

        /**
         * 비율.
         * @return 비율
         */
        public BigDecimal getShare() {
            return share;
        }

        /**
         * 직전 평균.
         * @return 직전 평균
         */
        public BigDecimal getAverage() {
            return average;
        }

        /**
         * 증감액.
         * @return 증감액
         */
        public BigDecimal getDelta() {
            return delta;
        }

        /**
         * 증감률, 기준 0이면 null.
         * @return 증감률, 기준 0이면 null
         */
        public BigDecimal getRate() {
            return rate;
        }
    }

    /** 소비 분석 결과. 생성 후 값은 변경하지 않는다. */
    public static final class Analysis implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<CategoryStat> categories;
        private final int baselineMonths;
        private final BigDecimal forecast;
        private final BigDecimal monthlySaving;
        private final BigDecimal annualSaving;
        private final boolean empty;

        /**
         * 소비 분석 결과 생성.
         * @param categories 카테고리 통계
         * @param baselineMonths 비교 개월 수
         * @param forecast 예상 지출
         * @param monthlySaving 월간 절감액
         * @param annualSaving 연간 절감액
         * @param empty 현재 지출 없음
         */
        public Analysis(
                List<CategoryStat> categories,
                int baselineMonths,
                BigDecimal forecast,
                BigDecimal monthlySaving,
                BigDecimal annualSaving,
                boolean empty) {
            this.categories = categories;
            this.baselineMonths = baselineMonths;
            this.forecast = forecast;
            this.monthlySaving = monthlySaving;
            this.annualSaving = annualSaving;
            this.empty = empty;
        }

        /**
         * 카테고리 통계.
         * @return 카테고리 통계
         */
        public List<CategoryStat> getCategories() {
            return categories;
        }

        /**
         * 비교 개월 수.
         * @return 비교 개월 수
         */
        public int getBaselineMonths() {
            return baselineMonths;
        }

        /**
         * 예상 지출.
         * @return 예상 지출
         */
        public BigDecimal getForecast() {
            return forecast;
        }

        /**
         * 월간 절감액.
         * @return 월간 절감액
         */
        public BigDecimal getMonthlySaving() {
            return monthlySaving;
        }

        /**
         * 연간 절감액.
         * @return 연간 절감액
         */
        public BigDecimal getAnnualSaving() {
            return annualSaving;
        }

        /**
         * 현재 지출 없음.
         * @return 현재 지출 없음
         */
        public boolean getEmpty() {
            return empty;
        }
    }

    /** 목표별 실제 및 가상 현황. 생성 후 값은 변경하지 않는다. */
    public static final class GoalStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Goal goal;
        private final BigDecimal virtual;
        private final BigDecimal actualRate;
        private final BigDecimal virtualRate;
        private final BigDecimal remaining;

        /**
         * 목표별 실제 및 가상 현황 생성.
         * @param goal 목표
         * @param virtual 가상 누적액
         * @param actualRate 실제 달성률
         * @param virtualRate 가상 진척도
         * @param remaining 목표까지 남은 금액
         */
        public GoalStatus(
                Goal goal,
                BigDecimal virtual,
                BigDecimal actualRate,
                BigDecimal virtualRate,
                BigDecimal remaining) {
            this.goal = goal;
            this.virtual = virtual;
            this.actualRate = actualRate;
            this.virtualRate = virtualRate;
            this.remaining = remaining;
        }

        /**
         * 목표.
         * @return 목표
         */
        public Goal getGoal() {
            return goal;
        }

        /**
         * 가상 누적액.
         * @return 가상 누적액
         */
        public BigDecimal getVirtual() {
            return virtual;
        }

        /**
         * 실제 달성률.
         * @return 실제 달성률
         */
        public BigDecimal getActualRate() {
            return actualRate;
        }

        /**
         * 가상 진척도.
         * @return 가상 진척도
         */
        public BigDecimal getVirtualRate() {
            return virtualRate;
        }

        /**
         * 목표까지 남은 금액.
         * @return 목표까지 남은 금액
         */
        public BigDecimal getRemaining() {
            return remaining;
        }
    }
}
