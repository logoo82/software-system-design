package household;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static household.Domain.*;

/** 처음 실행해볼 때 기능을 확인할 수 있도록 넣어둔 예시 데이터. */
public final class DemoData {
    private DemoData() {}

    /**
     * 보고서와 같은 숫자를 확인할 수 있도록 거래와 목표를 준비한다.
     * @return 2026년 10월 15일 기준 예시
     */
    public static State create() {
        List<Entry> entries = new ArrayList<>();

        // 지난 3개월 자료를 넣어서 소비 비교를 해볼 수 있게 한다.
        for (int i = 7; i <= 9; i++) {
            entries.add(
                    entry(
                            LocalDate.of(2026, i, 10),
                            Type.EXPENSE,
                            "식비",
                            new String[] {"100000", "120000", "140000"}[i - 7],
                            "이전 월 식비"));
            entries.add(entry(LocalDate.of(2026, i, 12), Type.EXPENSE, "교통비", "50000", "이전 월 교통비"));
            entries.add(entry(LocalDate.of(2026, i, 13), Type.EXPENSE, "기타", "80000", "이전 월 기타"));
        }

        // 이번 달 예시 금액은 보고서의 계산 결과에 맞춰둔다.
        entries.add(entry(LocalDate.of(2026, 10, 1), Type.INCOME, "용돈", "1000000", "월 생활비"));
        entries.add(entry(LocalDate.of(2026, 10, 10), Type.EXPENSE, "식비", "140000", "식료품과 식사"));
        entries.add(entry(LocalDate.of(2026, 10, 15), Type.EXPENSE, "식비", "10000", "점심 식사"));
        entries.add(entry(LocalDate.of(2026, 10, 13), Type.EXPENSE, "교통비", "50000", "교통카드 충전"));
        entries.add(entry(LocalDate.of(2026, 10, 14), Type.EXPENSE, "기타", "100000", "생활용품"));

        UUID goal = UUID.randomUUID();

        return new State(
                entries,
                Map.of(YearMonth.of(2026, 10), new BigDecimal("500000")),
                List.of(
                        new Goal(
                                goal,
                                "유럽 여행",
                                new BigDecimal("2000000"),
                                new BigDecimal("500000"))),
                List.of(
                        new NoSpend(
                                UUID.randomUUID(),
                                "배달 떡볶이",
                                new BigDecimal("30000"),
                                LocalDate.of(2026, 10, 15),
                                goal,
                                false)),
                YearMonth.of(2026, 7));
    }

    private static Entry entry(
            LocalDate date, Type type, String category, String amount, String description) {
        return new Entry(
                UUID.randomUUID(), date, type, category, new BigDecimal(amount), description);
    }
}
