package com.miracle.arcanesigils.gui.input;

import java.util.regex.Pattern;

/**
 * Input validation configuration for sign GUI text input.
 * Provides preset validations and builder for custom validation rules.
 */
public class InputValidation {

    private final int maxLength;
    private final Pattern pattern;
    private final String errorMessage;
    private final boolean allowColorCodes;

    // ============ Preset Validations ============

    /** Sigil IDs: lowercase letters, numbers, underscores only */
    public static final InputValidation SIGIL_ID = new InputValidation(
        32, "[a-z0-9_]+",
        "IDs must be lowercase letters, numbers, and underscores only",
        false
    );

    /** Display names: most characters allowed, supports color codes */
    public static final InputValidation DISPLAY_NAME = new InputValidation(
        48, null, null, true
    );

    /** File names: letters, numbers, dashes, underscores */
    public static final InputValidation FILENAME = new InputValidation(
        64, "[a-zA-Z0-9_-]+",
        "Filenames can only contain letters, numbers, dashes, and underscores",
        false
    );

    /** Parameter names: lowercase, no spaces */
    public static final InputValidation PARAM_NAME = new InputValidation(
        24, "[a-z][a-z0-9_]*",
        "Parameter names must start with a letter and contain only lowercase letters, numbers, and underscores",
        false
    );

    /** General text: moderate length, allows most characters */
    public static final InputValidation GENERAL_TEXT = new InputValidation(
        128, null, null, true
    );

    /** Short text: limited length for labels */
    public static final InputValidation SHORT_TEXT = new InputValidation(
        32, null, null, false
    );

    // ============ Constructor ============

    public InputValidation(int maxLength, String pattern, String errorMessage, boolean allowColorCodes) {
        this.maxLength = maxLength;
        this.pattern = pattern != null ? Pattern.compile(pattern) : null;
        this.errorMessage = errorMessage != null ? errorMessage : "Invalid input";
        this.allowColorCodes = allowColorCodes;
    }

    // ============ Validation ============

    /**
     * Validate input against this validation configuration.
     * @return ValidationResult with success/failure and error message
     */
    public ValidationResult validate(String input) {
        if (input == null) {
            return ValidationResult.fail("Input cannot be null");
        }

        // Check length
        if (input.length() > maxLength) {
            return ValidationResult.fail("Input too long (max " + maxLength + " characters)");
        }

        // Check pattern
        if (pattern != null && !pattern.matcher(input).matches()) {
            return ValidationResult.fail(errorMessage);
        }

        return ValidationResult.ok();
    }

    /**
     * Get the sanitized input (strip color codes if not allowed).
     */
    public String sanitize(String input) {
        if (input == null) return "";
        if (!allowColorCodes) {
            // Strip & and section symbols used for color codes
            return input.replaceAll("[&\u00A7][0-9a-fk-or]", "");
        }
        return input;
    }

    // ============ Getters ============

    public int getMaxLength() {
        return maxLength;
    }

    public boolean hasPattern() {
        return pattern != null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean allowsColorCodes() {
        return allowColorCodes;
    }

    // ============ Builder ============

    /**
     * Create a custom validation with builder pattern.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxLength = 128;
        private String pattern = null;
        private String errorMessage = "Invalid input";
        private boolean allowColorCodes = true;

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder pattern(String regex) {
            this.pattern = regex;
            return this;
        }

        public Builder errorMessage(String message) {
            this.errorMessage = message;
            return this;
        }

        public Builder allowColorCodes(boolean allow) {
            this.allowColorCodes = allow;
            return this;
        }

        public InputValidation build() {
            return new InputValidation(maxLength, pattern, errorMessage, allowColorCodes);
        }
    }

    // ============ Result Class ============

    /**
     * Result of input validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
