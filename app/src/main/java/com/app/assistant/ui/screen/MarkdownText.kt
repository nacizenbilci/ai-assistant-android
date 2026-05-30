package com.app.assistant.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.node.Text as CText

@Composable
fun MarkdownText(
    modifier: Modifier = Modifier,
    markdown: String,
) {
    val annotatedText =
        remember(markdown) {
            parseMarkdownToAnnotatedString(markdown)
        }
    Text(
        text = annotatedText,
        modifier = modifier,
    )
}

fun parseMarkdownToAnnotatedString(markdown: String): AnnotatedString {
    val parser = Parser.builder().build()
    val document = parser.parse(markdown)
    val builder = AnnotatedString.Builder()
    processNodes(document, builder)
    return builder.toAnnotatedString()
}

/**
 * Recursively processes nodes. We only append a newline if there's a *next sibling*,
 * preventing extra blank lines at the very end.
 */
private fun processNodes(
    node: Node,
    builder: AnnotatedString.Builder,
) {
    var child = node.firstChild
    while (child != null) {
        val nextSibling = child.next
        when (child) {
            is CText -> {
                builder.append(child.literal)
            }

            is Emphasis -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }

            is StrongEmphasis -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }

            is Code -> {
                // Inline code (single backticks)
                val start = builder.length
                builder.append(child.literal)
                val end = builder.length
                // Monospace but keep background transparent for inline code
                builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace),
                    start,
                    end,
                )
            }

            is Link -> {
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                // Annotate the text with the URL (useful if you want to make it clickable later)
                builder.addStringAnnotation(
                    tag = "URL",
                    annotation = child.destination,
                    start = start,
                    end = end,
                )
                // Style the link text
                builder.addStyle(
                    SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                    start,
                    end,
                )
            }

            is Paragraph -> {
                // Process paragraph content
                processNodes(child, builder)
                // Only append a newline if there's another sibling after this paragraph
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is Heading -> {
                // Insert a newline before headings (for spacing), unless it's the first node
                if (builder.isNotEmpty() && !builder.toString().endsWith("\n")) {
                    builder.append("\n")
                }
                val start = builder.length
                processNodes(child, builder)
                val end = builder.length
                // Map heading level to style
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
                // Only append a newline if there's another sibling
                if (nextSibling != null) {
                    builder.append("\n")
                }
            }

            is BlockQuote -> {
                // Optional: style block quotes in italics or different color
                val start = builder.length
                processNodes(child, builder)
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
                        // Prefix with a bullet
                        builder.append("• ")
                        processNodes(listItem, builder)
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
                        processNodes(listItem, builder)
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
                // Code block with triple backticks
                val start = builder.length
                // Insert the code exactly as-is
                builder.append(child.literal)
                val end = builder.length
                // Always dark background, white text
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                    ),
                    start,
                    end,
                )
                // Only append a newline if there's another sibling
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
                // Usually rendered as a space in Markdown
                builder.append(" ")
            }

            is HardLineBreak -> {
                builder.append("\n")
            }

            else -> {
                processNodes(child, builder)
            }
        }
        child = nextSibling
    }
}

/** Quick helper to check if the builder already has text. */
private fun AnnotatedString.Builder.isNotEmpty(): Boolean = this.length > 0
