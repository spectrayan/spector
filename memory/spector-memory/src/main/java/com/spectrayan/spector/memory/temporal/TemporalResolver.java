/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.temporal;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * Deterministic date resolution — NO LLM.
 * Uses ZoneId.systemDefault() for date calculations.
 */
public final class TemporalResolver {
    public record ResolvedText(String resolvedText, List<TemporalAnnotation> annotations) {}
    public record TemporalAnnotation(int startOffset, int endOffset, String original, Instant resolved) {}
    
    public ResolvedText resolve(String text, Instant referenceTime) {
        if (text == null || text.isBlank()) {
            return new ResolvedText(text, List.of());
        }
        
        List<TemporalAnnotation> annotations = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime refDate = referenceTime.atZone(zone);
        
        // Simple regex-based replacements
        Map<String, Integer> daysOffset = new LinkedHashMap<>();
        daysOffset.put("yesterday", -1);
        daysOffset.put("today", 0);
        daysOffset.put("tomorrow", 1);
        
        String resolvedText = text;
        for (Map.Entry<String, Integer> entry : daysOffset.entrySet()) {
            String word = entry.getKey();
            int offset = entry.getValue();
            Pattern p = Pattern.compile("(?i)\\b" + word + "\\b");
            Matcher m = p.matcher(resolvedText);
            
            while (m.find()) {
                ZonedDateTime target = refDate.plusDays(offset);
                Instant targetInstant = target.toInstant();
                annotations.add(new TemporalAnnotation(m.start(), m.end(), m.group(), targetInstant));
            }
        }
        
        // Pattern match for next/last <day_of_week>
        Pattern dowPattern = Pattern.compile("(?i)\\b(last|next)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b");
        Matcher dowM = dowPattern.matcher(resolvedText);
        while (dowM.find()) {
            String dir = dowM.group(1).toLowerCase(Locale.ROOT);
            String day = dowM.group(2).toUpperCase(Locale.ROOT);
            DayOfWeek targetDow = DayOfWeek.valueOf(day);
            ZonedDateTime target = refDate;
            if (dir.equals("last")) {
                target = target.with(java.time.temporal.TemporalAdjusters.previous(targetDow));
            } else {
                target = target.with(java.time.temporal.TemporalAdjusters.next(targetDow));
            }
            annotations.add(new TemporalAnnotation(dowM.start(), dowM.end(), dowM.group(), target.toInstant()));
        }
        
        // Pattern match for next/last week/month
        Pattern periodPattern = Pattern.compile("(?i)\\b(last|next)\\s+(week|month)\\b");
        Matcher pM = periodPattern.matcher(resolvedText);
        while (pM.find()) {
            String dir = pM.group(1).toLowerCase(Locale.ROOT);
            String period = pM.group(2).toLowerCase(Locale.ROOT);
            ZonedDateTime target = refDate;
            if (period.equals("week")) {
                target = dir.equals("last") ? target.minusWeeks(1) : target.plusWeeks(1);
            } else if (period.equals("month")) {
                target = dir.equals("last") ? target.minusMonths(1) : target.plusMonths(1);
            }
            annotations.add(new TemporalAnnotation(pM.start(), pM.end(), pM.group(), target.toInstant()));
        }
        
        // Sorting annotations by start offset
        annotations.sort(Comparator.comparingInt(TemporalAnnotation::startOffset));
        
        return new ResolvedText(resolvedText, annotations);
    }
}
