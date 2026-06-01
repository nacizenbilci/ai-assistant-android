package com.app.assistant.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

object MarkdownUtils {
    private val HeaderRegex = Regex("#+ ")
    private val BoldRegex = Regex("\\*\\*(.*?)\\*\\*")
    private val ItalicRegex = Regex("\\*(.*?)\\*")
    private val ImageRegex = Regex("!\\[.*?\\]\\(.*?\\)")
    private val LinkRegex = Regex("\\[.*?\\]\\(.*?\\)")
    private val CodeRegex = Regex("`{1,3}.*?`{1,3}")
    private val NewlinesRegex = Regex("\\n\\n")

    private val parser = Parser.builder().build()

    fun markdownToPlainText(markdown: String): String {
        var plainText = markdown
        plainText = plainText.replace(HeaderRegex, "") // Remove headers
        plainText = plainText.replace(BoldRegex, "$1") // Bold
        plainText = plainText.replace(ItalicRegex, "$1") // Italic
        plainText = plainText.replace(ImageRegex, "") // Images
        plainText = plainText.replace(LinkRegex, "") // Links
        plainText = plainText.replace(CodeRegex, "") // Inline code
        plainText = plainText.replace(NewlinesRegex, "\n") // Simplify newlines
        return plainText.trim()
    }

    fun parseMarkdownToAnnotatedString(
        markdown: String,
        linkColor: Color = Color.Blue
    ): AnnotatedString {
        val document = parser.parse(markdown)
        val builder = AnnotatedString.Builder()
        processNodes(document, builder, linkColor)
        return builder.toAnnotatedString()
    }

    private fun processNodes(
        node: Node,
        builder: AnnotatedString.Builder,
        linkColor: Color,
    ) {
        var child = node.firstChild
        while (child != null) {
            val nextSibling = child.next
            when (child) {
                is Text -> {
                    builder.append(child.literal)
                }

                is Emphasis -> {
                    val start = builder.length
                    processNodes(child, builder, linkColor)
                    val end = builder.length
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }

                is StrongEmphasis -> {
                    val start = builder.length
                    processNodes(child, builder, linkColor)
                    val end = builder.length
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }

                is Code -> {
                    // Inline code (single backticks)
                    val start = builder.length
                    builder.append(child.literal)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace),
                        start,
                        end,
                    )
                }

                is Link -> {
                    val start = builder.length
                    processNodes(child, builder, linkColor)
                    val end = builder.length
                    builder.addStringAnnotation(
                        tag = "URL",
                        annotation = child.destination,
                        start = start,
                        end = end,
                    )
                    builder.addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start,
                        end,
                    )
                }

                is Paragraph -> {
                    processNodes(child, builder, linkColor)
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is Heading -> {
                    if (builder.isNotEmpty() && !builder.toString().endsWith("\n")) {
                        builder.append("\n")
                    }
                    val start = builder.length
                    processNodes(child, builder, linkColor)
                    val end = builder.length
                    val headingStyle =
                        when (child.level) {
                            1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp)
                            2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            else -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    builder.addStyle(headingStyle, start, end)
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is BlockQuote -> {
                    val start = builder.length
                    processNodes(child, builder, linkColor)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic),
                        start,
                        end,
                    )
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is BulletList -> {
                    var listItem = child.firstChild
                    while (listItem != null) {
                        if (listItem is ListItem) {
                            builder.append("• ")
                            processNodes(listItem, builder, linkColor)
                            builder.append("\n")
                        }
                        listItem = listItem.next
                    }
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is OrderedList -> {
                    var index = child.startNumber
                    var listItem = child.firstChild
                    while (listItem != null) {
                        if (listItem is ListItem) {
                            builder.append("$index. ")
                            processNodes(listItem, builder, linkColor)
                            builder.append("\n")
                            index++
                        }
                        listItem = listItem.next
                    }
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is FencedCodeBlock -> {
                    val start = builder.length
                    builder.append(child.literal)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                        ),
                        start,
                        end,
                    )
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is IndentedCodeBlock -> {
                    val start = builder.length
                    builder.append(child.literal)
                    val end = builder.length
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                        ),
                        start,
                        end,
                    )
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is ThematicBreak -> {
                    builder.append("\n----------------\n")
                    if (nextSibling != null) {
                        builder.append("\n")
                    }
                }

                is SoftLineBreak -> {
                    builder.append(" ")
                }

                is HardLineBreak -> {
                    builder.append("\n")
                }

                else -> {
                    processNodes(child, builder, linkColor)
                }
            }
            child = nextSibling
        }
    }

    private fun AnnotatedString.Builder.isNotEmpty(): Boolean = this.length > 0
}
