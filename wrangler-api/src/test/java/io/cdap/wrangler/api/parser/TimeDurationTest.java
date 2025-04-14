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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link TimeDuration}
 */
public class TimeDurationTest {

    private static final long NANO_PER_MILLI = 1_000_000L;
    private static final long NANO_PER_SECOND = 1_000_000_000L;
    private static final long NANO_PER_MINUTE = NANO_PER_SECOND * 60L;
    private static final long NANO_PER_HOUR = NANO_PER_MINUTE * 60L;
    private static final long NANO_PER_DAY = NANO_PER_HOUR * 24L;

    @Test
    public void testValidDurations() {
        Assert.assertEquals(100 * NANO_PER_MILLI, new TimeDuration("100ms").getNanoseconds());
        Assert.assertEquals(100 * NANO_PER_MILLI, new TimeDuration("100MS").getNanoseconds());
        Assert.assertEquals(100 * NANO_PER_MILLI, new TimeDuration("100 ms").getNanoseconds()); // With space

        Assert.assertEquals(5 * NANO_PER_SECOND, new TimeDuration("5s").getNanoseconds());
        Assert.assertEquals(5 * NANO_PER_SECOND, new TimeDuration("5S").getNanoseconds());
        Assert.assertEquals(5 * NANO_PER_SECOND, new TimeDuration("5 s").getNanoseconds());

        Assert.assertEquals(10 * NANO_PER_MINUTE, new TimeDuration("10m").getNanoseconds());
        Assert.assertEquals(10 * NANO_PER_MINUTE, new TimeDuration("10 M ").getNanoseconds()); // With space

        Assert.assertEquals(2 * NANO_PER_HOUR, new TimeDuration("2h").getNanoseconds());
        Assert.assertEquals(2 * NANO_PER_HOUR, new TimeDuration("2 H").getNanoseconds());

        Assert.assertEquals(1 * NANO_PER_DAY, new TimeDuration("1d").getNanoseconds());
        Assert.assertEquals(1 * NANO_PER_DAY, new TimeDuration(" 1 D").getNanoseconds()); // Leading space
    }

    @Test
    public void testFractionalDurations() {
        Assert.assertEquals((long) (2.5 * NANO_PER_SECOND), new TimeDuration("2.5s").getNanoseconds());
        Assert.assertEquals((long) (0.5 * NANO_PER_HOUR), new TimeDuration("0.5h").getNanoseconds());
        Assert.assertEquals((long) (1500.75 * NANO_PER_MILLI), new TimeDuration("1500.75ms").getNanoseconds());
    }

    @Test
    public void testZeroAndNegativeDurations() {
        Assert.assertEquals(0L, new TimeDuration("0s").getNanoseconds());
        Assert.assertEquals(0L, new TimeDuration("0ms").getNanoseconds());
        Assert.assertEquals(-10 * NANO_PER_MINUTE, new TimeDuration("-10m").getNanoseconds());
    }

    @Test
    public void testGetters() {
        TimeDuration td1 = new TimeDuration("150.5ms");
        Assert.assertEquals(150.5, td1.getOriginalValue(), 0.001);
        Assert.assertEquals("ms", td1.getOriginalUnit());
        Assert.assertEquals("150.5ms", td1.getTokenString());
        Assert.assertEquals(TokenType.TIME_DURATION, td1.type());

        TimeDuration td2 = new TimeDuration(" 2 H "); // Test trimming and casing
        Assert.assertEquals(2.0, td2.getOriginalValue(), 0.001);
        Assert.assertEquals("h", td2.getOriginalUnit());
        Assert.assertEquals(" 2 H ", td2.getTokenString());
    }

    // --- Test Invalid Inputs ---

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNoNumber() {
        new TimeDuration("ms");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatWrongUnit() {
        new TimeDuration("10years");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatMissingUnit() {
        new TimeDuration("100"); // Unit is mandatory for TimeDuration
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatDoubleUnit() {
        new TimeDuration("10ms s");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNonNumeric() {
        new TimeDuration("abc ms");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testEmptyInput() {
        new TimeDuration("");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceInput() {
        new TimeDuration("  ");
    }
} 