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

import io.cdap.wrangler.api.annotations.PublicEvolving;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Represents a token containing a time duration value (e.g., 150ms, 2.5s, 1h).
 */
@PublicEvolving
public class TimeDuration implements Token {
    private final long nanos;
    private final String originalValue;
    private final String unit;
    private final TokenType tokenType = TokenType.TIME_DURATION; // Store type

    // Regex to capture value and unit (ms, s, m, h, d)
    private static final Pattern TIME_PATTERN = Pattern.compile("([+-]?\\d*\\.?\\d+)((?:m|M)(?:s|S)?|(?:s|S)|(?:h|H)|(?:d|D))", Pattern.CASE_INSENSITIVE);

    public TimeDuration(String token) throws IllegalArgumentException {
        this.originalValue = token;
        Matcher matcher = TIME_PATTERN.matcher(token);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("Invalid time duration format: '%s'. Expected format like '150ms', '2.5s', '10m', '1h', '2d'.", token));
        }

        double value = Double.parseDouble(matcher.group(1));
        String parsedUnit = matcher.group(2).toLowerCase(); // Normalize unit to lowercase
        this.unit = matcher.group(2); // Preserve original case for unit field

        switch (parsedUnit) {
            case "ms":
                this.nanos = (long) (value * TimeUnit.MILLISECONDS.toNanos(1));
                break;
            case "s":
                this.nanos = (long) (value * TimeUnit.SECONDS.toNanos(1));
                break;
            case "m": // minutes
                this.nanos = (long) (value * TimeUnit.MINUTES.toNanos(1));
                break;
            case "h": // hours
                this.nanos = (long) (value * TimeUnit.HOURS.toNanos(1));
                break;
            case "d": // days
                this.nanos = (long) (value * TimeUnit.DAYS.toNanos(1));
                break;
            default:
                 throw new IllegalArgumentException("Invalid time duration unit: " + parsedUnit);
        }
    }

    /**
     * @return The duration in nanoseconds.
     */
    public long getNanos() {
        return nanos;
    }

    /**
     * @return The original string representation of the time duration (e.g., "150ms").
     */
    public String getOriginalValue() {
        return originalValue;
    }

    /**
     * @return The unit part of the original string (e.g., "ms", "s", "h").
     */
    public String getUnit() {
        return unit;
    }

    @Override
    public Object value() {
        // Return the canonical value (nanos) as the primary value
        return nanos;
    }

    @Override
    public TokenType type() {
        return this.tokenType;
    }

    @Override
    public JsonElement toJson() {
        // Represent as the original string for JSON serialization
        return new JsonPrimitive(originalValue);
    }

    @Override
    public String toString() {
      return "TimeDuration{" +
        "nanos=" + nanos +
        ", originalValue='" + originalValue + '\'' +
        ", unit='" + unit + '\'' +
        '}';
    }

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeDuration that = (TimeDuration) o;
        // Compare based on essential fields: canonical nanos value and original string
        return nanos == that.nanos &&
               Objects.equals(originalValue, that.originalValue);
    }

    @Override
    public int hashCode() {
        // Hash based on essential fields
        return Objects.hash(nanos, originalValue);
    }
} 