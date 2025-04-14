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
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TimeDuration}
 */
public class TimeDurationTest {

    @Test
    public void testTimeDurationParsingAndConversion() {
        // Test different units
        Assert.assertEquals(150L * 1_000_000L, new TimeDuration("150ms").getNanos());
        Assert.assertEquals(2L * 1_000_000_000L, new TimeDuration("2s").getNanos());
        Assert.assertEquals(10L * 60L * 1_000_000_000L, new TimeDuration("10m").getNanos());
        Assert.assertEquals(1L * 60L * 60L * 1_000_000_000L, new TimeDuration("1h").getNanos());
        Assert.assertEquals(3L * 24L * 60L * 60L * 1_000_000_000L, new TimeDuration("3d").getNanos());

        // Test double values
        Assert.assertEquals((long)(2.5 * 1_000_000_000L), new TimeDuration("2.5s").getNanos());
        Assert.assertEquals((long)(0.5 * 60.0 * 60.0 * 1_000_000_000.0), new TimeDuration("0.5h").getNanos());
        Assert.assertEquals((long)(1.2 * 1_000_000L), new TimeDuration("1.2ms").getNanos());

        // Test case insensitivity
        Assert.assertEquals(150L * 1_000_000L, new TimeDuration("150MS").getNanos());
        Assert.assertEquals(150L * 1_000_000L, new TimeDuration("150Ms").getNanos());
        Assert.assertEquals(2L * 1_000_000_000L, new TimeDuration("2S").getNanos());
        Assert.assertEquals(10L * 60L * 1_000_000_000L, new TimeDuration("10M").getNanos());
        Assert.assertEquals(1L * 60L * 60L * 1_000_000_000L, new TimeDuration("1H").getNanos());
        Assert.assertEquals(3L * 24L * 60L * 60L * 1_000_000_000L, new TimeDuration("3D").getNanos());

        // Test original value and unit retrieval
        TimeDuration td = new TimeDuration("500ms");
        Assert.assertEquals("500ms", td.getOriginalValue());
        Assert.assertEquals("ms", td.getUnit());
        Assert.assertEquals(500L * 1_000_000L, td.getNanos());

        TimeDuration td2 = new TimeDuration("1.75H");
        Assert.assertEquals("1.75H", td2.getOriginalValue());
        Assert.assertEquals("H", td2.getUnit());
        Assert.assertEquals((long)(1.75 * 60.0 * 60.0 * 1_000_000_000.0), td2.getNanos());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNoUnit() {
        new TimeDuration("100");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatWrongUnit() {
        new TimeDuration("10years");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatMissingNumber() {
        new TimeDuration("s");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatWithSpace() {
        // Spaces are not handled by the current regex/logic
        new TimeDuration("10 s");
    }

    @Test
    public void testEquality() {
        TimeDuration t1 = new TimeDuration("1500ms");
        TimeDuration t2 = new TimeDuration("1.5s"); // Same value, different representation
        TimeDuration t3 = new TimeDuration("1500MS"); // Same value, different case
        TimeDuration t4 = new TimeDuration("1600ms"); // Different value
        TimeDuration t5 = new TimeDuration("1.5m"); // Different unit/value

        Assert.assertEquals(t1, t2);
        Assert.assertEquals(t1, t3);
        Assert.assertNotEquals(t1, t4);
        Assert.assertNotEquals(t1, t5);
        Assert.assertEquals(t1.hashCode(), t2.hashCode());
        Assert.assertEquals(t1.hashCode(), t3.hashCode());
        Assert.assertNotEquals(t1.hashCode(), t4.hashCode());
        Assert.assertNotEquals(t1.hashCode(), t5.hashCode());
    }
} 