package com.bestduo.api.dto;

/**
 * DB의 tier_grade SMALLINT (0=S ~ 4=D) ↔ API 문자열 변환
 */
public class TierGradeConverter {

    private static final String[] LABELS = {"S", "A", "B", "C", "D"};

    public static String toLabel(int grade) {
        if (grade < 0 || grade >= LABELS.length) {
            return "D";
        }
        return LABELS[grade];
    }

    public static int toGrade(String label) {
        return switch (label.toUpperCase()) {
            case "S" -> 0;
            case "A" -> 1;
            case "B" -> 2;
            case "C" -> 3;
            default -> 4;
        };
    }
}
