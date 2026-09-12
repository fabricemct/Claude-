package com.whatschat.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Common, non-identity emoji available for quick insertion into a message. */
val COMMON_EMOJIS = listOf(
    "😀", "😂", "🤣", "😊", "😍", "😘", "😉", "😎", "🤩", "🥳",
    "😢", "😭", "😡", "😱", "😴", "🤔", "🙄", "😅", "🤗", "🤭",
    "👍", "👎", "👏", "🙏", "💪", "🤝", "👋", "✌️", "🤞", "👌",
    "❤️", "💔", "💕", "🔥", "✨", "🎉", "🎂", "🎁", "☀️", "🌙",
    "⭐", "☕", "🍕", "🍔", "🍺", "⚽", "🎵", "📸", "💯", "✅"
)

/** Larger, more expressive emoji used as ready-made "stickers". */
val STICKER_EMOJIS = listOf(
    "😂", "😍", "🥳", "😎", "😭", "😡", "🤯", "🥰", "😴", "🤤",
    "👍", "👏", "🙌", "🤝", "💪", "🙏", "👋", "🤙", "✌️", "🤘",
    "❤️", "💯", "🔥", "✨", "🎉", "🎊", "🎂", "🌈", "⚡", "💥",
    "🐶", "🐱", "🐼", "🦄", "🍕", "🍺", "☕", "⚽", "🎮", "🏆"
)

@Composable
fun EmojiGridDialog(
    title: String,
    emojis: List<String>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                items(emojis) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = fontSize,
                        modifier = Modifier
                            .padding(6.dp)
                            .clickable {
                                onEmojiSelected(emoji)
                                onDismiss()
                            }
                    )
                }
            }
        },
        confirmButton = {}
    )
}
