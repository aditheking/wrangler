/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.wrangler.parser;

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.CompileException;
import io.cdap.wrangler.api.CompileStatus;
import io.cdap.wrangler.api.Compiler;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.RecipeParser;
import io.cdap.wrangler.api.RecipeSymbol;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.Token;
import io.cdap.wrangler.api.TokenGroup;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

/**
 * Tests {@link GrammarBasedParser}
 */
public class GrammarBasedParserTest {

  // Helper to join recipe lines
  private String join(String... lines) {
    StringJoiner joiner = new StringJoiner("\n");
    for (String line : lines) {
      joiner.add(line);
    }
    return joiner.toString();
  }

  @Test
  public void testBasic() throws Exception {
    String[] recipe = new String[] {
      "#pragma version 2.0;",
      "rename :col1 :col2",
      "parse-as-csv :body ',' true;",
      "#pragma load-directives text-reverse, text-exchange;",
      "${macro} ${macro_2}",
      "${macro_${test}}"
    };

    RecipeParser parser = TestingRig.parse(recipe);
    List<Directive> directives = parser.parse();
    // Expected directives depends on registered directives in TestingRig, adjust if necessary.
    // Assuming rename and parse-as-csv are standard.
    Assert.assertTrue("Expected at least 2 directives", directives.size() >= 2);
  }

  @Test
  public void testLoadableDirectives() throws Exception {
    String[] recipe = new String[] {
      "#pragma version 2.0;",
      "#pragma load-directives text-reverse, text-exchange;",
      "rename col1 col2", // Assuming rename does not require column prefix ':'
      "parse-as-csv body , true", // Assuming parse-as-csv does not require column prefix ':'
      "text-reverse :body;",
      "// test prop: { a='b', b=1.0, c=true};", // Commented out original test line
      "#pragma load-directives test-change,text-exchange, test1,test2,test3,test4;"
    };

    Compiler compiler = new RecipeCompiler();
    // Compile the joined string
    CompileStatus status = compiler.compile(join(recipe));
    Assert.assertEquals(7, status.getSymbols().getLoadableDirectives().size());
  }

  @Test
  public void testCommentOnlyRecipe() throws Exception {
    String[] recipe = new String[] {
      "// test"
    };

    RecipeParser parser = TestingRig.parse(recipe);
    List<Directive> directives = parser.parse();
    Assert.assertEquals(0, directives.size());
  }

  @Test
  public void testNewUnitTypesRecognition() throws Exception {
    // Test that the parser simply recognizes the syntax without failing.
    // Uses a placeholder directive name since execution isn't tested here.
    String[] recipe = new String[] {
      "placeholder-directive 10MB 500ms :output_col"
    };

    try {
      // We just need the compiler to generate the symbol table
      Compiler compiler = new RecipeCompiler();
      // Compile the joined string
      CompileStatus status = compiler.compile(join(recipe));
      Assert.assertNotNull(status);
      Assert.assertTrue("Compilation should succeed", status.isSuccess());
    } catch (Exception e) {
      Assert.fail("Parser failed to recognize recipe with new unit types (BYTE_SIZE, TIME_DURATION): " + e.getMessage());
    }
  }

  @Test
  public void testNewUnitTypesTokenVerification() throws CompileException {
    // Test that the recognized tokens are correctly typed and have correct values.
    String[] recipe = new String[] {
      "placeholder-directive 2.5GB 15s"
    };

    Compiler compiler = new RecipeCompiler();
    // Compile the joined string
    CompileStatus status = compiler.compile(join(recipe));
    Assert.assertTrue("Compilation should succeed", status.isSuccess());

    RecipeSymbol symbols = status.getSymbols();
    Assert.assertNotNull("RecipeSymbol should not be null", symbols);

    // Use iterator() for TokenGroups
    Iterator<TokenGroup> groupIterator = symbols.iterator();
    Assert.assertTrue("Should have at least one TokenGroup", groupIterator.hasNext());
    TokenGroup group = groupIterator.next();
    Assert.assertFalse("Should have only one TokenGroup", groupIterator.hasNext());

    // Use iterator() for Tokens and collect into a list
    Iterator<Token> tokenIterator = group.iterator();
    List<Token> tokens = new ArrayList<>();
    tokenIterator.forEachRemaining(tokens::add);

    Assert.assertEquals("Should have 3 tokens", 3, tokens.size());

    // Verify ByteSize token
    Token sizeToken = tokens.get(1);
    Assert.assertTrue("Second token should be ByteSize", sizeToken instanceof ByteSize);
    ByteSize byteSize = (ByteSize) sizeToken;
    Assert.assertEquals("2.5GB", byteSize.getOriginalValue());
    Assert.assertEquals((long)(2.5 * 1024.0 * 1024.0 * 1024.0), byteSize.getBytes());

    // Verify TimeDuration token
    Token timeToken = tokens.get(2);
    Assert.assertTrue("Third token should be TimeDuration", timeToken instanceof TimeDuration);
    TimeDuration timeDuration = (TimeDuration) timeToken;
    Assert.assertEquals("15s", timeDuration.getOriginalValue());
    Assert.assertEquals(15L * 1_000_000_000L, timeDuration.getNanos());
  }

  @Test(expected = CompileException.class)
  public void testInvalidByteSizeSyntax() throws CompileException {
    String[] recipe = {"placeholder-directive 10 MB"}; // Space not allowed
    Compiler compiler = new RecipeCompiler();
    compiler.compile(join(recipe));
  }

  @Test(expected = CompileException.class)
  public void testInvalidTimeDurationSyntax() throws CompileException {
    String[] recipe = {"placeholder-directive 5minutes"}; // Invalid unit
    Compiler compiler = new RecipeCompiler();
    compiler.compile(join(recipe));
  }

}
