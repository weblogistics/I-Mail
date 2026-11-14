package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.ui.theme.Spacing

/**
 * Shared "nothing here" visual for list screens (Inbox, Unified Inbox, Drafts, ...) — an icon
 * plus message reads more clearly as "genuinely empty" than plain centered text, which is easy
 * to mistake for a loading glitch or rendering error. [actionLabel]/[onAction] add an optional
 * button below the message for the cases where there's an obvious next step right here (e.g. "No
 * accounts yet" offering "Add account" directly, rather than just telling the user where to find
 * it) — omit both (the default) for a purely informational empty state.
 */
@Composable
fun EmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.sm).size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = Spacing.md)) {
                Text(actionLabel)
            }
        }
    }
}
