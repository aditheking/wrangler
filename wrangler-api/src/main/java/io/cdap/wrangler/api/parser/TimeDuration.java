/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.wrangler.api.annotations.Public;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a time duration token parsed from a directive argument (e.g., "100ms", "2.5s", "5m").
 * Provides the duration in nanoseconds.
 */
@Public
public class TimeDuration implements Token, Serializable {
    private static final long serialVersionUID = -1234567890123456789L; // Example serialVersionUID

    // Pattern to capture number and unit: (number) (unit) - allows optional space
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(-?[0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+)$", Pattern.CASE_INSENSITIVE);

    // Nanoseconds multipliers
    private static final long NANO_PER_MILLI = 1_000_000L;
    private static final long NANO_PER_SECOND = 1_000_000_000L;
    private static final long NANO_PER_MINUTE = NANO_PER_SECOND * 60L;
    private static final long NANO_PER_HOUR = NANO_PER_MINUTE * 60L;
    private static final long NANO_PER_DAY = NANO_PER_HOUR * 24L;

    private final long nanoseconds;
    private final double originalValue;
    private final String originalUnit;
    private final String tokenString;
    private final TokenType tokenType = TokenType.TIME_DURATION;

    public TimeDuration(String token) {
        this.tokenString = token;
        Matcher matcher = DURATION_PATTERN.matcher(token.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid time duration format: '%s'. Expected format like '100ms', '2.5s', '5m', '1h', '0.5d'.", token));
        }

        String valueStr = matcher.group(1);
        String unitStr = matcher.group(2).toLowerCase(); // Normalize unit to lower case

        try {
            this.originalValue = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid numeric value in time duration: '%s' from token '%s'.", valueStr, token), e);
        }

        long multiplier = 0L;
        this.originalUnit = unitStr; // Store the original unit string

        switch (unitStr) {
            case "ms":
                multiplier = NANO_PER_MILLI;
                break;
            case "s":
                multiplier = NANO_PER_SECOND;
                break;
            case "m": // minute
                multiplier = NANO_PER_MINUTE;
                break;
            case "h": // hour
                multiplier = NANO_PER_HOUR;
                break;
            case "d": // day
                multiplier = NANO_PER_DAY;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unknown time unit '%s' in token '%s'. Supported units: ms, s, m, h, d.", unitStr, token));
        }

        // Use double for intermediate calculation to handle fractions of units before converting to long nanoseconds
        this.nanoseconds = (long) (this.originalValue * multiplier);
    }

    /**
     * @return The duration represented by this token in nanoseconds.
     */
    public long getNanoseconds() {
        return nanoseconds;
    }

     /**
     * @return The original numeric value parsed from the token string.
     */
    public double getOriginalValue() {
        return originalValue;
    }

    /**
     * @return The original unit string (e.g., "ms", "s") parsed from the token string (normalized to lowercase).
     */
    public String getOriginalUnit() {
        return originalUnit;
    }

    /**
     * @return The original, unmodified token string.
     */
    public String getTokenString() {
        return tokenString;
    }

    // --- Implementation of Token interface methods ---

    @Override
    public Object value() {
        // Return the canonical value (nanoseconds)
        return this.nanoseconds;
    }

    @Override
    public TokenType type() {
        return this.tokenType;
    }

    @Override
    public JsonElement toJson() {
        JsonObject jo = new JsonObject();
        jo.addProperty("type", type().name());
        jo.addProperty("value_nanoseconds", nanoseconds);
        jo.addProperty("original_value", originalValue);
        jo.addProperty("original_unit", originalUnit);
        jo.addProperty("original_string", tokenString);
        return jo;
    }

    // --- Overridden Object methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeDuration that = (TimeDuration) o;
        return nanoseconds == that.nanoseconds &&
               Double.compare(that.originalValue, originalValue) == 0 &&
               Objects.equals(originalUnit, that.originalUnit) &&
               Objects.equals(tokenString, that.tokenString) &&
               tokenType == that.tokenType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nanoseconds, originalValue, originalUnit, tokenString, tokenType);
    }

    @Override
    public String toString() {
        return "TimeDuration{" +
               "nanoseconds=" + nanoseconds +
               ", originalValue=" + originalValue +
               ", originalUnit='" + originalUnit + '\'' +
               ", tokenString='" + tokenString + '\'' +
               ", tokenType=" + tokenType +
               '}';
    }
} 