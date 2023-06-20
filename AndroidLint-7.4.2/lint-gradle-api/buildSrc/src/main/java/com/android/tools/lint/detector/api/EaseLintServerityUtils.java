package com.android.tools.lint.detector.api;

import com.android.tools.lint.model.LintModelSeverity;

public class EaseLintServerityUtils {
    public static LintModelSeverity getModelSeverity(Severity severity) {
        LintModelSeverity lm = null;
        switch (severity) {
            case FATAL:
                lm = LintModelSeverity.FATAL;

                break;
            case ERROR:
                lm = LintModelSeverity.ERROR;
                break;
            case WARNING:
                lm = LintModelSeverity.WARNING;
                break;
            case INFORMATIONAL:
                lm = LintModelSeverity.INFORMATIONAL;
                break;
            case IGNORE:
            default:
                lm = LintModelSeverity.IGNORE;
                break;
        }

        return lm;
    }

}
