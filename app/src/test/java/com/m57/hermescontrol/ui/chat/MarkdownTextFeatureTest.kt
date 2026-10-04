package com.m57.hermescontrol.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.m57.hermescontrol.theme.HermesStatusColors
import com.m57.hermescontrol.theme.StatusBlue
import com.m57.hermescontrol.theme.StatusBlueContainer
import com.m57.hermescontrol.theme.StatusGreen
import com.m57.hermescontrol.theme.StatusGreenContainer
import com.m57.hermescontrol.theme.StatusRed
import com.m57.hermescontrol.theme.StatusRedContainer
import com.m57.hermescontrol.theme.StatusYellow
import com.m57.hermescontrol.theme.StatusYellowContainer
import com.m57.hermescontrol.theme.searchHighlightColors
import com.m57.hermescontrol.util.BidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val DEFAULT_HIGHLIGHTS =
    searchHighlightColors(
        HermesStatusColors(
            success = StatusGreen,
            successContainer = StatusGreenContainer,
            onSuccess = Color.White,
            warning = StatusYellow,
            warningContainer = StatusYellowContainer,
            onWarning = Color.White,
            error = StatusRed,
            errorContainer = StatusRedContainer,
            onError = Color.White,
            info = StatusBlue,
            infoContainer = StatusBlueContainer,
            onInfo = Color.White,
        ),
    )

/**
 * Verifies the hand-rolled Markdown parser covers the feature set requested for issue #572.
 * Each test asserts the block/inline structure actually parses —
 * not just that it compiles. Inline assertions avoid referencing Compose text types (FontWeight,
 * BaselineShift, etc.) that aren't on the unit-test classpath; we inspect SpanStyle fields
 * structurally instead.
 */
class MarkdownTextFeatureTest {
    @Test
    fun unmatchedInnerEmphasisDoesNotSuppressOuterBold() {
        val parsed = parseInline("**foo * bar**", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("foo * bar", parsed.text)
        assertTrue(parsed.spanStyles.any { it.start == 0 && it.end == 9 && it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun linkDestinationStarsAreOpaqueToOuterEmphasis() {
        val source = "**[foo](https://example.com/a*b)**"
        val parsed = parseInline(source, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("foo", parsed.text)
        assertTrue(parsed.spanStyles.any { it.start == 0 && it.end == 3 && it.item.fontWeight == FontWeight.Bold })
        assertEquals(
            "https://example.com/a*b",
            (parsed.getLinkAnnotations(0, 3).single().item as androidx.compose.ui.text.LinkAnnotation.Url).url,
        )
    }

    @Test
    fun tripleEmphasisRecursivelyRendersOpaqueCode() {
        val parsed = parseInline("***outer `***` tail***", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("outer *** tail", parsed.text)
        assertTrue(
            parsed.spanStyles.any {
                it.start == 0 && it.end == parsed.length &&
                    it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
            },
        )
        assertTrue(
            parsed.spanStyles.any {
                it.start == 6 && it.end == 9 && it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace
            },
        )
    }

    @Test(timeout = 5000)
    fun emphasisIndexHandlesManyUnmatchedRunsWithoutSuffixRescans() {
        val source = "***x **y *z ".repeat(10000)
        emphasisPairs(source, emptyMap())
        val nested = "**a *b ".repeat(1000) + "tail" + "* **".repeat(1000)
        parseInline(nested, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
    }

    @Test
    fun styledSearchHitsKeepTheirFormatting() {
        listOf(
            "***Needle***",
            "~~Needle~~",
            "==Needle==",
            "^Needle^",
            "~Needle~",
            "<kbd>Needle</kbd>",
            "[**Needle**](https://example.com)",
            "`Needle`",
        ).forEach { source ->
            val parsed = parseInline(source, Color.Black, "needle", true, Color.Blue, DEFAULT_HIGHLIGHTS)
            assertEquals("Needle", parsed.text)
            assertTrue(
                parsed.spanStyles.any {
                    it.start == 0 && it.end == 6 && it.item.background == DEFAULT_HIGHLIGHTS.currentSearchBackground
                },
            )
        }
    }

    @Test
    fun codeSearchOverlayPreservesSyntaxAndHighlightsEveryHit() {
        val original =
            androidx.compose.ui.text.buildAnnotatedString {
                append("Needle needle")
                addStyle(
                    androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    0,
                    6,
                )
            }
        val result = original.withSearchHighlights("needle", false, DEFAULT_HIGHLIGHTS)
        assertEquals(original.text, result.text)
        assertTrue(result.spanStyles.containsAll(original.spanStyles))
        assertEquals(2, result.spanStyles.count { it.item.background == DEFAULT_HIGHLIGHTS.searchBackground })
        assertEquals(original, original.withSearchHighlights("", false, DEFAULT_HIGHLIGHTS))
    }

    @Test
    fun testNestedInlineStyles_preserveCodeAndEmphasisFormatting() {
        val parsed = parseInline("**bold `code` and *italic***", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

        assertEquals("bold code and italic", parsed.toString())
        assertTrue(parsed.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(parsed.spanStyles.any { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
        assertTrue(parsed.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        val codeStart = parsed.indexOf("code")
        assertTrue(
            parsed.spanStyles.any {
                it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold &&
                    it.start <= codeStart &&
                    it.end >= codeStart + 4
            },
        )
        assertTrue(
            parsed.spanStyles.any {
                it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace &&
                    it.start <= codeStart &&
                    it.end >= codeStart + 4
            },
        )
    }

    @Test
    fun testLinkLabel_nestedEmphasisAndCode() {
        val parsed =
            parseInline(
                "[**bold `code`**](https://example.com)",
                Color.Black,
                "",
                false,
                Color.Blue,
                DEFAULT_HIGHLIGHTS,
            )

        assertEquals("bold code", parsed.toString())
        assertTrue(parsed.getLinkAnnotations(0, parsed.length).isNotEmpty())
        assertTrue(parsed.spanStyles.any { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
    }

    @Test
    fun testCodeSpan_doesNotParseInnerEmphasis() {
        val parsed = parseInline("`**literal**`", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

        assertEquals("**literal**", parsed.toString())
        assertFalse(parsed.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }

    @Test
    fun testInlineCode_matchesSameLengthBacktickRunAndPreservesUnmatchedRun() {
        val parsed = parseInline("``a ` tick`` and `unfinished", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

        assertEquals("a ` tick and `unfinished", parsed.toString())
        assertTrue(parsed.spanStyles.any { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
    }

    // 1. TABLES
    @Test
    fun testTable_parsesHeaderAndRows() {
        val md =
            """
            | Name | Age | Role |
            |------|:---:|-----:|
            | Alice | 30 | dev |
            | Bob | 25 | ops |
            """.trimIndent()
        val blocks = parseBlocks(md)
        val table = blocks.singleOrNull { it is MdBlock.Table } as MdBlock.Table?
        assertTrue("table block expected", table != null)
        assertEquals(listOf("Name", "Age", "Role"), table!!.header)
        assertEquals(2, table.rows.size)
        assertEquals("Alice", table.rows[0][0])
        assertEquals("ops", table.rows[1][2])
        // alignment inference: center, left, right
        assertEquals(TableAlign.CENTER, table.alignments[1])
        assertEquals(TableAlign.RIGHT, table.alignments[2])
    }

    // 2. MATH
    @Test
    fun testDisplayMath_parsesFormula() {
        val block = parseBlocks("\$\$\\frac{1}{2}\$\$").single() as MdBlock.Math

        assertEquals("\\frac{1}{2}", block.latex)
    }

    @Test
    fun testMultilineDisplayMath_preservesFormula() {
        val block = parseBlocks("\$\$\n\\frac{1}{2}\n+ x\n\$\$").single() as MdBlock.Math

        assertEquals("\\frac{1}{2}\n+ x", block.latex)
    }

    @Test
    fun testInlineMath_stripsDelimitersFromFallbackText() {
        val parsed = parseInline("Energy \$E=mc^2\$.", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

        assertEquals("Energy E=mc^2.", parsed.toString())
    }

    @Test
    fun testInlineMath_insideBold_preservesStyle() {
        val markup = buildInlineMathMarkup("**Energy \$E\$**")
        val marker = markup.math.single().marker
        val parsed = parseInline(markup.source, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val markerIndex = parsed.indexOf(marker)

        assertEquals("Energy $marker", parsed.toString())
        assertTrue(parsed.spanStyles.any { markerIndex in it.start until it.end })
    }

    @Test
    fun testInlineMath_insideLink_preservesLink() {
        val markup = buildInlineMathMarkup("[Energy \$E\$](https://example.com)")
        val marker = markup.math.single().marker
        val parsed = parseInline(markup.source, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val markerIndex = parsed.indexOf(marker)

        assertEquals("Energy $marker", parsed.toString())
        assertTrue(parsed.getLinkAnnotations(markerIndex, markerIndex + 1).isNotEmpty())
    }

    @Test
    fun testBracketDisplayMath_parsesFormula() {
        val block = parseBlocks("\\[\n\\frac{1}{2}\n\\]").single() as MdBlock.Math

        assertEquals("\\frac{1}{2}", block.latex)
    }

    @Test
    fun testParenthesizedInlineMath_stripsDelimitersFromFallbackText() {
        val parsed = parseInline("Energy \\(E=mc^2\\).", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

        assertEquals("Energy E=mc^2.", parsed.toString())
    }

    @Test
    fun testEscapedParenthesizedDelimiter_staysText() {
        val segments = splitInlineMath("""\\(not math\\)""")

        assertEquals(listOf(InlineMathSegment.Text("""\\(not math\\)""")), segments)
    }

    @Test
    fun testEscapedParenthesizedClose_doesNotEndMath() {
        val segments = splitInlineMath("""\(a\\)b\)""")

        assertEquals(listOf(InlineMathSegment.Math("""a\\)b""")), segments)
    }

    @Test
    fun testUnmatchedDisplayDelimiter_staysParagraph() {
        val block = parseBlocks("\$\$\n\\frac{1}{2}").single() as MdBlock.Paragraph

        assertEquals("\$\$\n\\frac{1}{2}", block.text)
    }

    @Test
    fun testFencedMathDelimiter_staysCode() {
        val block = parseBlocks("```latex\n\$\$x\$\$\n```").single() as MdBlock.Code

        assertEquals("\$\$x\$\$", block.code)
    }

    @Test
    fun testMultiBacktickCodeSpans_doNotParseMath() {
        val samples =
            listOf(
                "``\$x\$``",
                "```\\(x\\)```",
            )

        samples.forEach { sample ->
            assertEquals(listOf(InlineMathSegment.Text(sample)), splitInlineMath(sample))
        }
    }

    // 3. STRIKETHROUGH
    @Test
    fun testStrikethrough_parses() {
        val an = parseInline("~~gone~~", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("gone", an.toString())
        assertTrue(an.spanStyles.any { it.item.textDecoration != null })
    }

    // 4. BOLD + ITALIC in same word (***bolditalic***)
    @Test
    fun testBoldItalic_combined() {
        val an = parseInline("***both***", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("both", an.toString())
        assertTrue(an.spanStyles.isNotEmpty())
    }

    // 5. TASK LIST / CHECKBOX
    @Test
    fun testTaskList_parsesCheckedAndUnchecked() {
        val md =
            """
            - [x] done
            - [ ] todo
            """.trimIndent()
        val blocks = parseBlocks(md)
        assertEquals(2, blocks.size)
        val done = blocks[0] as MdBlock.Task
        val todo = blocks[1] as MdBlock.Task
        assertTrue(done.checked)
        assertFalse(todo.checked)
        assertEquals("done", done.text)
        assertEquals("todo", todo.text)
    }

    // 6. HORIZONTAL RULE (---)
    @Test
    fun testHorizontalRule_parses() {
        val md =
            """
            above

            ---

            below
            """.trimIndent()
        val blocks = parseBlocks(md)
        assertTrue(blocks.any { it is MdBlock.Hr })
    }

    // 7. FOOTNOTES
    @Test
    fun testFootnotes_collectedAndRendered() {
        val md =
            """
            Science is cool.[^1]

            [^1]: A famous claim.
            """.trimIndent()
        val blocks = parseBlocks(md)
        val fn = blocks.singleOrNull { it is MdBlock.Footnotes } as MdBlock.Footnotes?
        assertTrue(fn != null)
        assertEquals("1", fn!!.notes[0].id)
        assertEquals("A famous claim.", fn.notes[0].text)
    }

    // 8. DEFINITION LIST
    @Test
    fun testDefinitionList_parses() {
        val md =
            """
            Term
            : first definition
            : second definition
            """.trimIndent()
        val dl = parseBlocks(md).singleOrNull { it is MdBlock.DefList } as MdBlock.DefList?
        assertTrue(dl != null)
        assertEquals("Term", dl!!.items[0].term)
        assertEquals(2, dl.items[0].definitions.size)
        assertEquals("first definition", dl.items[0].definitions[0])
    }

    // 9. HIGHLIGHT
    @Test
    fun testHighlight_parses() {
        val an = parseInline("==mark==", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("mark", an.toString())
        assertTrue(an.spanStyles.any { it.item.background != Color.Unspecified })
    }

    // 10. SUPERSCRIPT / SUBSCRIPT
    @Test
    fun testSuperscriptAndSubscript_parse() {
        val sup = parseInline("x^2^", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertTrue(sup.spanStyles.any { it.item.baselineShift != null })
        val sub = parseInline("H~2~O", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertTrue(sub.spanStyles.any { it.item.baselineShift != null })
    }

    // 11. KEYBOARD KEYS <kbd>
    @Test
    fun testKbd_parses() {
        val an =
            parseInline("Press <kbd>Ctrl</kbd>+<kbd>C</kbd>", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertTrue(an.toString().contains("Ctrl"))
        assertTrue(an.toString().contains("C"))
        assertTrue(an.spanStyles.any { it.item.fontFamily != null })
    }

    // --- regression: issue ref "#572" must NOT become a heading ---
    @Test
    fun testHashRef_notHeading() {
        val blocks = parseBlocks("#572 should stay text")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    // --- regression: streaming gate handled in composable, parser must not split inline code ---
    @Test
    fun testInlineCode_preserved() {
        val an = parseInline("use `val x = 1` here", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("use val x = 1 here", an.toString())
        assertTrue(an.spanStyles.any { it.item.fontFamily != null })
    }

    // 12. STANDALONE MARKDOWN IMAGE -> MdBlock.Image (agent-media rendering)
    @Test
    fun testStandaloneImage_parsesToImageBlock() {
        val md = "![cute cat](https://example.com/cat.jpg)"
        val block = parseBlocks(md).singleOrNull() as? MdBlock.Image
        assertTrue("expected a single Image block", block != null)
        assertEquals("https://example.com/cat.jpg", block!!.uri)
        assertEquals("cute cat", block.alt)
    }

    // 12b. data: URL (base64 inline media) also accepted as an image uri
    @Test
    fun testImage_dataUrl_accepted() {
        val uri = "data:image/jpeg;base64,/9j/abc"
        val md = "![pic]($uri)"
        val block = parseBlocks(md).singleOrNull() as? MdBlock.Image
        assertTrue("data: URL should parse to Image block", block != null)
        assertEquals(uri, block!!.uri)
    }

    // 12c. inline image inside a paragraph is left as text (scope guard)
    @Test
    fun testInlineImage_inParagraph_staysText() {
        val md = "see this ![cat](https://x/cat.png) for reference"
        val blocks = parseBlocks(md)
        assertTrue("should remain a paragraph", blocks.single() is MdBlock.Paragraph)
    }

    // 13. Attachment isGif check (issue #721)
    @Test
    fun testAttachment_isGif() {
        val gifAttachment =
            com.m57.hermescontrol.data.model.Attachment(
                uri = "content://media/1.gif",
                name = "test.gif",
                mimeType = "image/gif",
            )
        assertTrue(gifAttachment.isImage)
        assertTrue(gifAttachment.isGif)

        val nonGifAttachment =
            com.m57.hermescontrol.data.model.Attachment(
                uri = "content://media/1.jpg",
                name = "test.jpg",
                mimeType = "image/jpeg",
            )
        assertTrue(nonGifAttachment.isImage)
        assertFalse(nonGifAttachment.isGif)
    }

    @Test
    fun testResolveImageSource_gatewayDownloadDropsQueryCredentials() {
        val source =
            resolveImageSource(
                "https://gateway.example.com/api/files/download?" +
                    "path=%2Ftmp%2Fimage.png&token=must-not-survive",
            )

        assertEquals("/tmp/image.png", source.model)
        assertEquals("/tmp/image.png", source.gatewayPath)
    }

    @Test
    fun testResolveImageSource_externalUrlRemainsExternal() {
        val source = resolveImageSource("https://images.example.com/cat.gif")

        assertEquals("https://images.example.com/cat.gif", source.model)
        assertNull(source.gatewayPath)
    }

    @Test
    fun testOrderedList_looseListPreservesNumbers() {
        val markdown =
            """
            1. First

            2. Second

            3. Third
            """.trimIndent()

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Ordered>()

        assertEquals(3, blocks.size)
        assertEquals(listOf(1, 2, 3), blocks.map { it.index })
        assertEquals(listOf("First", "Second", "Third"), blocks.map { it.text })
    }

    @Test
    fun testOrderedList_customStartNumberPreserved() {
        val markdown =
            """
            2. Two
            3. Three
            """.trimIndent()

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Ordered>()

        assertEquals(listOf(2, 3), blocks.map { it.index })
    }

    @Test
    fun testOrderedList_tightListPreservesNumbers() {
        val markdown =
            """
            1. One
            2. Two
            3. Three
            """.trimIndent()

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Ordered>()

        assertEquals(listOf(1, 2, 3), blocks.map { it.index })
    }

    @Test
    fun testNestedLists_preserveMixedTypesAndLevels() {
        val markdown =
            """
            - top bullet
              1. nested number
                - deep bullet
              2. second number
            - second bullet
            """.trimIndent()

        val blocks = parseBlocks(markdown)

        assertEquals(
            listOf(
                MdBlock.Bullet("top bullet", level = 0),
                MdBlock.Ordered(1, "nested number", level = 1),
                MdBlock.Bullet("deep bullet", level = 2),
                MdBlock.Ordered(2, "second number", level = 1),
                MdBlock.Bullet("second bullet", level = 0),
            ),
            blocks,
        )
    }

    @Test
    fun testNestedTasks_preserveCheckedStateAndLevels() {
        val markdown =
            """
            - [ ] parent
              - [x] child
                - [ ] grandchild
            """.trimIndent()

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Task>()

        assertEquals(
            listOf(
                MdBlock.Task(checked = false, text = "parent", level = 0),
                MdBlock.Task(checked = true, text = "child", level = 1),
                MdBlock.Task(checked = false, text = "grandchild", level = 2),
            ),
            blocks,
        )
    }

    @Test
    fun testNestedLists_preserveSourceNumbers() {
        val markdown =
            """
            3. Three
            1. One
            1. One again
            """.trimIndent()

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Ordered>()

        assertEquals(listOf(3, 1, 1), blocks.map { it.index })
    }

    @Test
    fun testNestedLists_supportTabsAndContinuationLines() {
        val markdown = "- root\n\t- child\n\t  continued line"

        val blocks = parseBlocks(markdown).filterIsInstance<MdBlock.Bullet>()

        assertEquals(
            listOf(
                MdBlock.Bullet("root", level = 0),
                MdBlock.Bullet("child continued line", level = 1),
            ),
            blocks,
        )
    }

    @Test
    fun testNestedLists_doNotFlattenIndentedBlockContent() {
        val markdown =
            """
            - item
              > quoted block
            """.trimIndent()

        val blocks = parseBlocks(markdown)

        assertEquals(MdBlock.Bullet("item"), blocks.first())
        assertEquals(MdBlock.Paragraph("  > quoted block"), blocks.last())
    }

    @Test
    fun testNestedLists_doNotFlattenIndentedCodeFence() {
        val markdown =
            """
            - item
              ```kotlin
              val answer = 42
              ```
            """.trimIndent()

        val blocks = parseBlocks(markdown)

        assertEquals(MdBlock.Bullet("item"), blocks.first())
        assertTrue(blocks.last() is MdBlock.Paragraph)
    }

    @Test
    fun testNestedLists_doNotAttachContentThatDedentsPastCurrentItem() {
        val markdown =
            """
            - root
                - child
              outside child
            """.trimIndent()

        val blocks = parseBlocks(markdown)

        assertEquals(MdBlock.Bullet("root", level = 0), blocks[0])
        assertEquals(MdBlock.Bullet("child", level = 1), blocks[1])
        assertEquals(MdBlock.Paragraph("  outside child"), blocks[2])
    }

    @Test
    fun testNestedLists_useActualMarkerWidthForContinuations() {
        val cases =
            listOf(
                "-   item\n  too shallow" to MdBlock.Bullet("item"),
                "10.   item\n    too shallow" to MdBlock.Ordered(10, "item"),
                "- [ ]   item\n      too shallow" to MdBlock.Task(false, "item"),
            )

        cases.forEach { (markdown, expectedItem) ->
            val blocks = parseBlocks(markdown)

            assertEquals(expectedItem, blocks.first())
            assertTrue(blocks.last() is MdBlock.Paragraph)
        }
    }

    @Test
    fun testNestedLists_doNotFlattenIndentedCodeBlock() {
        val markdown = "- item\n      val answer = 42"

        val blocks = parseBlocks(markdown)

        assertEquals(MdBlock.Bullet("item"), blocks.first())
        assertEquals(MdBlock.Paragraph("      val answer = 42"), blocks.last())
    }

    @Test
    fun testNestedCodeBlock_fourBackticksPreserveThreeBackticks() {
        val markdown =
            """
            ````markdown
            Here is a nested block:
            ```python
            print("hello")
            ```
            ````
            """.trimIndent()

        val block = parseBlocks(markdown).single() as MdBlock.Code

        assertEquals("markdown", block.language)
        assertTrue(block.code.contains("```python"))
        assertTrue(block.code.contains("print(\"hello\")"))
    }

    @Test
    fun testTildeCodeBlock_parses() {
        val markdown =
            """
            ~~~json
            {"key": "value"}
            ~~~
            """.trimIndent()

        val block = parseBlocks(markdown).single() as MdBlock.Code

        assertEquals("json", block.language)
        assertEquals("{\"key\": \"value\"}", block.code)
    }

    @Test
    fun testLongerFenceRequiresMatchingCloseLength() {
        val markdown =
            """
            `````
            inner
            ````
            still inner
            `````
            """.trimIndent()

        val block = parseBlocks(markdown).single() as MdBlock.Code

        assertTrue(block.code.contains("````"))
        assertTrue(block.code.contains("still inner"))
    }

    @Test(timeout = 5000)
    fun testUnequalBacktickRuns_haveBoundedParsingCost() {
        val input = (1..2000).joinToString("x") { "`".repeat(it) }
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals(input, parsed.text)
        assertTrue(parsed.spanStyles.none { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
    }

    @Test
    fun testNestedEmphasis_bothDirectionsAndOpaqueCode() {
        listOf(
            "*outer **bold** tail*" to "outer bold tail",
            "**outer *bold* tail**" to "outer bold tail",
            "*outer **bold***" to "outer bold",
            "**outer *bold***" to "outer bold",
            "*outer `**opaque**` **bold** tail*" to "outer **opaque** bold tail",
            "**outer `*opaque*` *bold* tail**" to "outer *opaque* bold tail",
        ).forEach { (input, expected) ->
            val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
            assertEquals(input, expected, parsed.text)
            val start = parsed.text.lastIndexOf("bold")
            assertTrue(
                parsed.spanStyles.any {
                    it.start <= start && it.end >= start + 4 && it.item.fontWeight == FontWeight.Bold
                },
            )
            assertTrue(
                parsed.spanStyles.any {
                    it.start <= start && it.end >= start + 4 && it.item.fontStyle == FontStyle.Italic
                },
            )
        }
    }

    @Test
    fun testRtlRecursiveSnippets_isolateWholeSlashIdentifierAndKeepAnnotations() {
        listOf("**foo/bar**", "*foo/bar*", "[**foo/bar**](https://example.com)").forEach { markup ->
            val parsed =
                parseInline("مرحبا $markup نهاية", Color.Black, "foo/bar", false, Color.Blue, DEFAULT_HIGHLIGHTS)
            assertEquals("مرحبا ${BidiUtils.LRI}foo/bar${BidiUtils.PDI} نهاية", parsed.text)
            val start = parsed.text.indexOf("foo/bar")
            assertTrue(
                parsed.spanStyles.any {
                    it.start <= start && it.end >= start + 7 &&
                        (it.item.fontWeight == FontWeight.Bold || it.item.fontStyle == FontStyle.Italic)
                },
            )
            assertTrue(
                parsed.spanStyles.any {
                    it.start == start - 1 && it.end == start + 8 &&
                        it.item.background == DEFAULT_HIGHLIGHTS.searchBackground
                },
            )
            if (markup.startsWith("[")) {
                val link = parsed.getLinkAnnotations(start, start + 7).single()
                assertEquals("https://example.com", (link.item as androidx.compose.ui.text.LinkAnnotation.Url).url)
            }
        }
    }

    // 25. ARABIC BIDI MIXED TEXT (issue #1044)
    @Test
    fun testArabicMixedWithInlineCode_wrapsWithLtrIsolate() {
        val input = "تم اختبار كود `val x = 1` بنجاح"
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val expected = "تم اختبار كود ${BidiUtils.LRI}val x = 1${BidiUtils.PDI} بنجاح"
        assertEquals(expected, parsed.toString())
    }

    @Test
    fun testArabicMixedWithNeutralInlineCode_wrapsWithLtrIsolate() {
        listOf("123", "--", "/").forEach { code ->
            val parsed = parseInline("شغّل `$code` الآن", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)

            assertEquals("شغّل ${BidiUtils.LRI}$code${BidiUtils.PDI} الآن", parsed.toString())
        }
    }

    @Test
    fun testArabicMixedWithBoldEnglish_wrapsWithLtrIsolate() {
        val input = "هذا النص يحتوي على **Android** داخل فقرة"
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val expected = "هذا النص يحتوي على ${BidiUtils.LRI}Android${BidiUtils.PDI} داخل فقرة"
        assertEquals(expected, parsed.toString())
    }

    @Test
    fun testEnglishWithInlineCode_doesNotWrapWithIsolate() {
        val input = "Run `val x = 1` here"
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        assertEquals("Run val x = 1 here", parsed.toString())
    }

    @Test
    fun testArabicStartingWithEnglishWord_wrapsEnglishInLtrIsolate() {
        val input = "Okay سكرت كلشي وصار كامل عربي"
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val expected = "${BidiUtils.LRI}Okay${BidiUtils.PDI} سكرت كلشي وصار كامل عربي"
        assertEquals(expected, parsed.toString())
    }

    @Test
    fun testArabicWithBareUrl_wrapsUrlInLtrIsolate() {
        val input = "راجع https://example.com/path ثم تابع"
        val parsed = parseInline(input, Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val expected = "راجع ${BidiUtils.LRI}https://example.com/path${BidiUtils.PDI} ثم تابع"
        assertEquals(expected, parsed.toString())
    }

    @Test
    fun testParagraphFinalBareUrl_doesNotIncludePresentationRlmInLinkTarget() {
        val parsed =
            parseInline("راجع https://example.com/path", Color.Black, "", false, Color.Blue, DEFAULT_HIGHLIGHTS)
        val anchored = anchorTrailingRtlPresentation(parsed, isRtl = true)
        val urlStart = anchored.indexOf("https://")
        val links = anchored.getLinkAnnotations(urlStart, anchored.length)

        assertEquals(
            "راجع ${BidiUtils.LRI}https://example.com/path${BidiUtils.PDI}${BidiUtils.RLM}",
            anchored.toString(),
        )
        assertEquals(1, links.size)
        assertEquals(
            "https://example.com/path",
            (links.single().item as androidx.compose.ui.text.LinkAnnotation.Url).url,
        )
        assertEquals(anchored.length - 1, links.single().end)
    }

    @Test
    fun testEnglishSearchInArabicParagraph_preservesIsolationAndHighlightStyle() {
        val parsed =
            parseInline(
                "ابحث عن Android الآن",
                Color.Black,
                "Android",
                false,
                Color.Blue,
                DEFAULT_HIGHLIGHTS,
            )
        val highlightedText = "${BidiUtils.LRI}Android${BidiUtils.PDI}"
        val highlightStart = parsed.indexOf(highlightedText)
        val highlightEnd = highlightStart + highlightedText.length

        assertEquals(8, highlightStart)
        assertEquals(17, highlightEnd)
        assertTrue("English query should remain LTR-isolated", highlightStart >= 0)
        assertTrue(
            "isolated English query should retain its search highlight",
            parsed.spanStyles.any { style ->
                style.start == highlightStart &&
                    style.end == highlightEnd &&
                    style.item.background == DEFAULT_HIGHLIGHTS.searchBackground &&
                    style.item.color == DEFAULT_HIGHLIGHTS.searchForeground
            },
        )
    }

    @Test
    fun testTableWithOneRtlCell_keepsAmbientColumnDirection() {
        val markdown =
            """
            | Name | Status |
            |------|--------|
            | Alice | جاهز |
            """.trimIndent()
        val table = parseBlocks(markdown).single() as MdBlock.Table

        assertTrue(BidiUtils.isRtlText(table.rows.single()[1]))
        assertEquals(LayoutDirection.Ltr, tableStructureDirection(LayoutDirection.Ltr))
        assertEquals(listOf("Name", "Status"), table.header)
        assertEquals(listOf("Alice", "جاهز"), table.rows.single())
    }
}
