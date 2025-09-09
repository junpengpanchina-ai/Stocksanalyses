package com.stocksanalyses.service;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TradingCalendarService {
    private final LocalTime regularOpen = LocalTime.of(9, 30);
    private final LocalTime regularClose = LocalTime.of(16, 0);
    private final Set<LocalDate> holidays = new HashSet<>();
    private final Set<LocalDate> halts = new HashSet<>();

    public boolean isTradingDay(LocalDate date) {
        if (date.getDayOfWeek().getValue() >= 6) return false; // weekend
        if (holidays.contains(date)) return false;
        if (halts.contains(date)) return false;
        return true;
    }

    public boolean isOpen(ZonedDateTime zdt) {
        LocalDate d = zdt.toLocalDate();
        if (!isTradingDay(d)) return false;
        LocalTime t = zdt.toLocalTime();
        return !t.isBefore(regularOpen) && !t.isAfter(regularClose);
    }

    public void loadHolidays(List<String> isoDates) {
        if (isoDates == null) return;
        for (String d : isoDates) holidays.add(LocalDate.parse(d));
    }

    public void loadHalts(List<String> isoDates) {
        if (isoDates == null) return;
        for (String d : isoDates) halts.add(LocalDate.parse(d));
    }
}


