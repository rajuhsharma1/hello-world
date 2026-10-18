package com.spa.operations;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

@RestController
public class StatisticsController {

    private final JdbcTemplate db;
    private final Clock clock;

    public StatisticsController(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public static LocalDate from(String period, LocalDate date) {
        return switch (period) {
            case "WEEK" -> date.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            );
            case "MONTH" -> date.withDayOfMonth(1);
            case "YEAR" -> date.withDayOfYear(1);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose WEEK, MONTH or YEAR."
            );
        };
    }

    public static LocalDate until(String period, LocalDate from) {
        return switch (period) {
            case "WEEK" -> from.plusWeeks(1);
            case "MONTH" -> from.plusMonths(1);
            case "YEAR" -> from.plusYears(1);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid period."
            );
        };
    }

    @GetMapping("/api/statistics")
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
    )
    public Map<String, Object> statistics(
            @RequestParam int year,
            @RequestParam(defaultValue = "MONTH") String period,
            @RequestParam LocalDate date
    ) {
        if (
                year < 2000 ||
                        year > 2100 ||
                        date.getYear() < 2000 ||
                        date.getYear() > 2100
        ) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Choose a year between 2000 and 2100."
        );
        var begin = from(period, date);
        var end = until(period, begin);
        var start = LocalDate.of(year, 1, 1);
        var months = db.queryForList(
                "select m.month_no as \"month\",coalesce(c.revenue,0) revenue,coalesce(p.revenue,0) previous_revenue,coalesce(e.expenses,0) expenses,coalesce(c.visits,0) visits from generate_series(1,12) m(month_no) left join (select extract(month from day)::integer as month_no,sum(amount) revenue,count(*) visits from sale where day>=? and day<? group by 1)c on c.month_no=m.month_no left join (select extract(month from day)::integer as month_no,sum(amount) revenue from sale where day>=? and day<? group by 1)p on p.month_no=m.month_no left join (select extract(month from day)::integer as month_no,sum(amount) expenses from expense where day>=? and day<? group by 1)e on e.month_no=m.month_no order by m.month_no",
                start,
                start.plusYears(1),
                start.minusYears(1),
                start,
                start,
                start.plusYears(1)
        );
        var people = db.queryForList(
                "select st.id,st.name,st.role,count(s.id) visits,count(distinct s.contact_number) unique_clients,coalesce(sum(s.amount),0) revenue from staff st left join sale s on s.staff_id=st.id and s.day>=? and s.day<? group by st.id,st.name,st.role order by visits desc,st.name,st.id",
                begin,
                end
        );
        return Map.of(
                "year",
                year,
                "previousYear",
                year - 1,
                "months",
                months,
                "staff",
                people,
                "period",
                period,
                "from",
                begin,
                "until",
                end,
                "businessDate",
                LocalDate.now(clock),
                "currency",
                "INR"
        );
    }
}
