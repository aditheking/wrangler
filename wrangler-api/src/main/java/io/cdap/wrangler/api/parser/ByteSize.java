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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a byte size value specified as a token, e.g., "10KB", "1.5MB".
 */
@Public
public class ByteSize implements Token {
    private static final long serialVersionUID = -865938567919409583L;
    private static final Pattern BYTE_PATTERN = Pattern.compile(
            "^(-?[0-9]+(?:\\.[0-9]+)?)\\s*([kKmMgGtTpP]?)B?$", Pattern.CASE_INSENSITIVE);
    private static final long KB_MULTIPLIER = 1024L;
    private static final long MB_MULTIPLIER = KB_MULTIPLIER * 1024L;
    private static final long GB_MULTIPLIER = MB_MULTIPLIER * 1024L;
    private static final long TB_MULTIPLIER = GB_MULTIPLIER * 1024L;
    private static final long PB_MULTIPLIER = TB_MULTIPLIER * 1024L;

    private final long bytes;
    private final double originalValue;
    private final String unit;
    private final String tokenString; // Store original token string
    private final TokenType tokenType = TokenType.BYTE_SIZE; // Store type

    public ByteSize(String token) {
        this.tokenString = token;
        Matcher matcher = BYTE_PATTERN.matcher(token.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid byte size format: '%s'. Expected format like '10KB', '1.5MB', '1024'.", token));
        }

        String valueStr = matcher.group(1);
        String unitChar = matcher.group(2);

        try {
            this.originalValue = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid numeric value in byte size: '%s' from token '%s'.", valueStr, token), e);
        }

        long multiplier = 1L;
        this.unit = (unitChar == null || unitChar.isEmpty()) ? "B" : unitChar.toUpperCase() + "B";

        if (unitChar != null && !unitChar.isEmpty()) {
            switch (Character.toUpperCase(unitChar.charAt(0))) {
                case 'K':
                    multiplier = KB_MULTIPLIER;
                    break;
                case 'M':
                    multiplier = MB_MULTIPLIER;
                    break;
                case 'G':
                    multiplier = GB_MULTIPLIER;
                    break;
                case 'T':
                    multiplier = TB_MULTIPLIER;
                    break;
                case 'P':
                    multiplier = PB_MULTIPLIER;
                    break;
            }
        }
        this.bytes = (long) (this.originalValue * multiplier);
    }

    public long getBytes() {
        return bytes;
    }

    public double getOriginalValue() {
        return originalValue;
    }

    public String getUnit() {
        return unit;
    }

    public String getTokenString() {
        return tokenString;
    }

    // --- Implementation of Token interface methods ---

    @Override
    public Object value() {
        return this.bytes;
    }

    @Override
    public TokenType type() {
        return this.tokenType;
    }

    @Override
    public JsonElement toJson() {
        JsonObject jo = new JsonObject();
        jo.addProperty("type", type().name());
        jo.addProperty("value_bytes", bytes);
        jo.addProperty("original_value", originalValue);
        jo.addProperty("original_unit", unit);
        jo.addProperty("original_string", tokenString);
        return jo;
    }

    // --- Overridden Object methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ByteSize byteSize = (ByteSize) o;
        return bytes == byteSize.bytes &&
               Double.compare(byteSize.originalValue, originalValue) == 0 &&
               Objects.equals(unit, byteSize.unit) &&
               Objects.equals(tokenString, byteSize.tokenString) &&
               tokenType == byteSize.tokenType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes, originalValue, unit, tokenString, tokenType);
    }

    @Override
    public String toString() {
        return "ByteSize{" +
               "bytes=" + bytes +
               ", originalValue=" + originalValue +
               ", unit='" + unit + '\'' +
               ", tokenString='" + tokenString + '\'' +
               ", tokenType=" + tokenType +
               '}';
    }
} 