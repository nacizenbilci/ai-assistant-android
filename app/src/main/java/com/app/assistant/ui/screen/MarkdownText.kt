package com.app.assistant.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

import androidx.compose.ui.tooling.preview.Preview
import com.app.assistant.ui.theme.AssistantTheme

@Composable
fun MarkdownText(
    modifier: Modifier = Modifier,
    markdown: String,
    linkColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
) {
    val annotatedText =
        remember(markdown, linkColor) {
            com.app.assistant.util.MarkdownUtils.parseMarkdownToAnnotatedString(markdown, linkColor)
        }
    Text(
        text = annotatedText,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun MarkdownTextPreview() {
    AssistantTheme {
        MarkdownText(
            modifier = Modifier.padding(16.dp),
            markdown = """
                # Markdown Preview
                This is a **bold** text and *italic* text.
                - List item 1
                - List item 2
                
                [Link to Google](https://google.com)
            """.trimIndent()
        )
    }
}

