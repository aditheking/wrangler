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
 * Tests {@link ByteSize}
 */
public class ByteSizeTest {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final long TB = GB * 1024L;
    private static final long PB = TB * 1024L;

    @Test
    public void testValidByteSizes() {
        Assert.assertEquals(10L, new ByteSize("10").getBytes());
        Assert.assertEquals(10L, new ByteSize("10b").getBytes());
        Assert.assertEquals(10L, new ByteSize("10B").getBytes());

        Assert.assertEquals(10 * KB, new ByteSize("10k").getBytes());
        Assert.assertEquals(10 * KB, new ByteSize("10K").getBytes());
        Assert.assertEquals(10 * KB, new ByteSize("10kb").getBytes());
        Assert.assertEquals(10 * KB, new ByteSize("10KB").getBytes());

        Assert.assertEquals(15 * MB, new ByteSize("15m").getBytes());
        Assert.assertEquals(15 * MB, new ByteSize("15M").getBytes());
        Assert.assertEquals(15 * MB, new ByteSize("15mb").getBytes());
        Assert.assertEquals(15 * MB, new ByteSize("15MB").getBytes());

        Assert.assertEquals(2 * GB, new ByteSize("2g").getBytes());
        Assert.assertEquals(2 * GB, new ByteSize("2GB").getBytes());

        Assert.assertEquals(1 * TB, new ByteSize("1t").getBytes());
        Assert.assertEquals(1 * TB, new ByteSize("1TB").getBytes());

        Assert.assertEquals(5 * PB, new ByteSize("5p").getBytes());
        Assert.assertEquals(5 * PB, new ByteSize("5PB").getBytes());

        // Test with spaces
        Assert.assertEquals(20 * KB, new ByteSize(" 20 KB ").getBytes());
    }

    @Test
    public void testFractionalByteSizes() {
        Assert.assertEquals((long) (1.5 * MB), new ByteSize("1.5MB").getBytes());
        Assert.assertEquals((long) (0.5 * GB), new ByteSize("0.5GB").getBytes());
        Assert.assertEquals((long) (1024.5 * KB), new ByteSize("1024.5KB").getBytes());
    }

    @Test
    public void testZeroAndNegativeByteSizes() {
        Assert.assertEquals(0L, new ByteSize("0").getBytes());
        Assert.assertEquals(0L, new ByteSize("0KB").getBytes());
        Assert.assertEquals(-10 * KB, new ByteSize("-10KB").getBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNoNumber() {
        new ByteSize("KB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatWrongUnit() {
        new ByteSize("10XB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatDoubleUnit() {
        new ByteSize("10KBM");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNonNumeric() {
        new ByteSize("abcMB");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testEmptyInput() {
        new ByteSize("");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceInput() {
        new ByteSize("  ");
    }

     @Test
     public void testGetOriginalValueAndUnit() {
         ByteSize bs1 = new ByteSize("1.5GB");
         Assert.assertEquals(1.5, bs1.getOriginalValue(), 0.001);
         Assert.assertEquals("GB", bs1.getUnit());

         ByteSize bs2 = new ByteSize("2048");
         Assert.assertEquals(2048, bs2.getOriginalValue(), 0.001);
         Assert.assertEquals("B", bs2.getUnit());

         ByteSize bs3 = new ByteSize("10k");
         Assert.assertEquals(10, bs3.getOriginalValue(), 0.001);
         Assert.assertEquals("KB", bs3.getUnit());
     }

    @Test
    public void testByteSizeParsingAndConversion() {
        // Test integer values
        Assert.assertEquals(10L * 1024L, new ByteSize("10KB").getBytes());
        Assert.assertEquals(5L * 1024L * 1024L, new ByteSize("5MB").getBytes());
        Assert.assertEquals(2L * 1024L * 1024L * 1024L, new ByteSize("2GB").getBytes());
        Assert.assertEquals(1L * 1024L * 1024L * 1024L * 1024L, new ByteSize("1TB").getBytes());
        Assert.assertEquals(3L * 1024L * 1024L * 1024L * 1024L * 1024L, new ByteSize("3PB").getBytes());

        // Test double values
        Assert.assertEquals((long)(1.5 * 1024.0 * 1024.0), new ByteSize("1.5MB").getBytes());
        Assert.assertEquals((long)(0.5 * 1024.0 * 1024.0 * 1024.0), new ByteSize("0.5GB").getBytes());

        // Test case insensitivity and optional 'B'
        Assert.assertEquals(10L * 1024L, new ByteSize("10kb").getBytes());
        Assert.assertEquals(10L * 1024L, new ByteSize("10k").getBytes());
        Assert.assertEquals(5L * 1024L * 1024L, new ByteSize("5mB").getBytes());
        Assert.assertEquals(5L * 1024L * 1024L, new ByteSize("5m").getBytes());

        // Test original value and unit retrieval
        ByteSize bs = new ByteSize("123GB");
        Assert.assertEquals("123GB", bs.getOriginalValue());
        Assert.assertEquals("GB", bs.getUnit());
        Assert.assertEquals(123L * 1024L * 1024L * 1024L, bs.getBytes());

        ByteSize bs2 = new ByteSize("2.7p"); // Petabytes without B
        Assert.assertEquals("2.7p", bs2.getOriginalValue());
        Assert.assertEquals("P", bs2.getUnit()); // Unit should still capture P
        Assert.assertEquals((long)(2.7 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0), bs2.getBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatNoUnit() {
        new ByteSize("100");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatMissingNumber() {
        new ByteSize("MB");
    }

     @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormatWithSpace() {
        // Spaces are not handled by the current regex/logic
        new ByteSize("10 KB");
    }

    @Test
    public void testEquality() {
      ByteSize b1 = new ByteSize("10KB");
      ByteSize b2 = new ByteSize("10kb"); // Different case, same value
      ByteSize b3 = new ByteSize("10K"); // Without B, same value
      ByteSize b4 = new ByteSize("11KB"); // Different value
      ByteSize b5 = new ByteSize("10MB"); // Different unit

      Assert.assertEquals(b1, b2);
      Assert.assertEquals(b1, b3);
      Assert.assertNotEquals(b1, b4);
      Assert.assertNotEquals(b1, b5);
      Assert.assertEquals(b1.hashCode(), b2.hashCode());
      Assert.assertEquals(b1.hashCode(), b3.hashCode());
      Assert.assertNotEquals(b1.hashCode(), b4.hashCode());
      Assert.assertNotEquals(b1.hashCode(), b5.hashCode());
    }
} 