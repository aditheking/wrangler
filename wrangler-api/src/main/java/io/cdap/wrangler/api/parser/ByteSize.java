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
import com.google.gson.JsonPrimitive;
import io.cdap.wrangler.api.annotations.PublicEvolving;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a token containing a byte size value (e.g., 10KB, 1.5MB).
 */
@PublicEvolving
public class ByteSize implements Token {
    private final long bytes;
    private final String originalValue;
    private final String unit;
    private final TokenType tokenType = TokenType.BYTE_SIZE; // Store type

    private static final Pattern BYTE_PATTERN = Pattern.compile("([+-]?\\d*\\.?\\d+)([kKmMgGtTpP])B?", Pattern.CASE_INSENSITIVE);
    private static final long KB = 1024L;
    private static final long MB = 1024L * KB;
    private static final long GB = 1024L * MB;
    private static final long TB = 1024L * GB;
    private static final long PB = 1024L * TB;

    public ByteSize(String token) throws IllegalArgumentException {
        this.originalValue = token;
        Matcher matcher = BYTE_PATTERN.matcher(token);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("Invalid byte size format: '%s'. Expected format like '10KB', '1.5MB'.", token));
        }

        double value = Double.parseDouble(matcher.group(1));
        String parsedUnit = matcher.group(2).toUpperCase();
        this.unit = parsedUnit + (token.toUpperCase().endsWith("B") ? "B" : "");

        switch (parsedUnit) {
            case "K":
                this.bytes = (long) (value * KB);
                break;
            case "M":
                this.bytes = (long) (value * MB);
                break;
            case "G":
                this.bytes = (long) (value * GB);
                break;
            case "T":
                this.bytes = (long) (value * TB);
                break;
            case "P":
                this.bytes = (long) (value * PB);
                break;
            default:
                 throw new IllegalArgumentException("Invalid byte size unit: " + parsedUnit);
        }
    }

    /**
     * @return The size in bytes.
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * @return The original string representation of the byte size (e.g., "10KB").
     */
    public String getOriginalValue() {
        return originalValue;
    }

    /**
     * @return The unit part of the original string (e.g., "KB", "MB").
     */
    public String getUnit() {
        return unit;
    }

    @Override
    public Object value() {
      // Return the canonical value (bytes) as the primary value
      return bytes;
    }

    @Override
    public TokenType type() {
        return this.tokenType;
    }

    @Override
    public JsonElement toJson() {
        // Represent as the original string for JSON serialization, consistent with other tokens
        return new JsonPrimitive(originalValue);
    }

    @Override
    public String toString() {
      return "ByteSize{" +
        "bytes=" + bytes +
        ", originalValue='" + originalValue + '\'' +
        ", unit='" + unit + '\'' +
        '}';
    }

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteSize byteSize = (ByteSize) o;
        // Compare based on essential fields: canonical bytes value and original string
        return bytes == byteSize.bytes &&
               Objects.equals(originalValue, byteSize.originalValue);
    }

    @Override
    public int hashCode() {
        // Hash based on essential fields
        return Objects.hash(bytes, originalValue);
    }
} 