package kr.ac.jnu.household;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** CP3 대표 함수의 입력값을 묶은 일반 명령 클래스 모음.
 * @author 김승혁 223247
 * @version 0.4
 */
public final class Commands {
    private Commands() { }
    /** 요청 작업. */
    public enum Action {
        /** 생성. */ CREATE,
        /** 수정. */ UPDATE,
        /** 삭제. */ DELETE,
        /** 조회. */ QUERY,
        /** 예산 설정. */ SET_BUDGET,
        /** 저축 추가. */ ADD_SAVING,
        /** 꾹참기 등록. */ REGISTER,
        /** 목표 연결. */ LINK_GOAL,
        /** 실제 저축 반영. */ APPLY_ACTUAL
    }

    /** 거래 변경 및 AND 검색 명령. 생성 후 값은 변경하지 않는다. */
    public static final class TransactionCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Action action;
        private final UUID id;
        private final LocalDate date;
        private final Domain.Type type;
        private final String category;
        private final BigDecimal amount;
        private final String description;
        private final LocalDate from;
        private final LocalDate to;
        private final String keyword;
        /**
         * 거래 변경 및 AND 검색 명령 생성.
         * @param action 작업
         * @param id 수정·삭제 ID
         * @param date 거래일
         * @param type 거래 유형
         * @param category 카테고리
         * @param amount 금액
         * @param description 상세 내용
         * @param from 조회 시작일
         * @param to 조회 종료일
         * @param keyword 검색어
         */
        public TransactionCommand(Action action, UUID id, LocalDate date, Domain.Type type, String category, BigDecimal amount, String description, LocalDate from, LocalDate to, String keyword) {
            this.action = action;
            this.id = id;
            this.date = date;
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.description = description;
            this.from = from;
            this.to = to;
            this.keyword = keyword;
        }
        /**
         * 작업.
         * @return 작업
         */
        public Action getAction() { return action; }
        /**
         * 수정·삭제 ID.
         * @return 수정·삭제 ID
         */
        public UUID getId() { return id; }
        /**
         * 거래일.
         * @return 거래일
         */
        public LocalDate getDate() { return date; }
        /**
         * 거래 유형.
         * @return 거래 유형
         */
        public Domain.Type getType() { return type; }
        /**
         * 카테고리.
         * @return 카테고리
         */
        public String getCategory() { return category; }
        /**
         * 금액.
         * @return 금액
         */
        public BigDecimal getAmount() { return amount; }
        /**
         * 상세 내용.
         * @return 상세 내용
         */
        public String getDescription() { return description; }
        /**
         * 조회 시작일.
         * @return 조회 시작일
         */
        public LocalDate getFrom() { return from; }
        /**
         * 조회 종료일.
         * @return 조회 종료일
         */
        public LocalDate getTo() { return to; }
        /**
         * 검색어.
         * @return 검색어
         */
        public String getKeyword() { return keyword; }
    }

    /** 월별 예산 및 조회 명령. 생성 후 값은 변경하지 않는다. */
    public static final class BudgetCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Action action;
        private final YearMonth month;
        private final BigDecimal budget;
        /**
         * 월별 예산 및 조회 명령 생성.
         * @param action QUERY 또는 SET_BUDGET
         * @param month 조회 월
         * @param budget 설정할 예산
         */
        public BudgetCommand(Action action, YearMonth month, BigDecimal budget) {
            this.action = action;
            this.month = month;
            this.budget = budget;
        }
        /**
         * QUERY 또는 SET_BUDGET.
         * @return QUERY 또는 SET_BUDGET
         */
        public Action getAction() { return action; }
        /**
         * 조회 월.
         * @return 조회 월
         */
        public YearMonth getMonth() { return month; }
        /**
         * 설정할 예산.
         * @return 설정할 예산
         */
        public BigDecimal getBudget() { return budget; }
    }

    /** 목표 관리 명령. 생성 후 값은 변경하지 않는다. */
    public static final class GoalCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Action action;
        private final UUID id;
        private final String name;
        private final BigDecimal target;
        private final BigDecimal actual;
        /**
         * 목표 관리 명령 생성.
         * @param action 작업
         * @param id 목표 ID
         * @param name 목표 이름
         * @param target 목표 금액
         * @param actual 새 저축 총액 또는 추가 금액
         */
        public GoalCommand(Action action, UUID id, String name, BigDecimal target, BigDecimal actual) {
            this.action = action;
            this.id = id;
            this.name = name;
            this.target = target;
            this.actual = actual;
        }
        /**
         * 작업.
         * @return 작업
         */
        public Action getAction() { return action; }
        /**
         * 목표 ID.
         * @return 목표 ID
         */
        public UUID getId() { return id; }
        /**
         * 목표 이름.
         * @return 목표 이름
         */
        public String getName() { return name; }
        /**
         * 목표 금액.
         * @return 목표 금액
         */
        public BigDecimal getTarget() { return target; }
        /**
         * 새 저축 총액 또는 추가 금액.
         * @return 새 저축 총액 또는 추가 금액
         */
        public BigDecimal getActual() { return actual; }
    }

    /** 꾹참기 명령. 생성 후 값은 변경하지 않는다. */
    public static final class NoSpendCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Action action;
        private final UUID id;
        private final String item;
        private final BigDecimal amount;
        private final LocalDate date;
        private final UUID goalId;
        private final LocalDate from;
        private final LocalDate to;
        /**
         * 꾹참기 명령 생성.
         * @param action 작업
         * @param id 기록 ID
         * @param item 소비 항목
         * @param amount 예정 금액
         * @param date 기록일
         * @param goalId 선택 목표 ID
         * @param from 조회 시작일
         * @param to 조회 종료일
         */
        public NoSpendCommand(Action action, UUID id, String item, BigDecimal amount, LocalDate date, UUID goalId, LocalDate from, LocalDate to) {
            this.action = action;
            this.id = id;
            this.item = item;
            this.amount = amount;
            this.date = date;
            this.goalId = goalId;
            this.from = from;
            this.to = to;
        }
        /**
         * 작업.
         * @return 작업
         */
        public Action getAction() { return action; }
        /**
         * 기록 ID.
         * @return 기록 ID
         */
        public UUID getId() { return id; }
        /**
         * 소비 항목.
         * @return 소비 항목
         */
        public String getItem() { return item; }
        /**
         * 예정 금액.
         * @return 예정 금액
         */
        public BigDecimal getAmount() { return amount; }
        /**
         * 기록일.
         * @return 기록일
         */
        public LocalDate getDate() { return date; }
        /**
         * 선택 목표 ID.
         * @return 선택 목표 ID
         */
        public UUID getGoalId() { return goalId; }
        /**
         * 조회 시작일.
         * @return 조회 시작일
         */
        public LocalDate getFrom() { return from; }
        /**
         * 조회 종료일.
         * @return 조회 종료일
         */
        public LocalDate getTo() { return to; }
    }
}
