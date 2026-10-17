package com.songly.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.songly.shared.model.Post
import com.songly.shared.util.RelativeTime

/**
 * Karta posta — wspólna dla feedu, społeczności, profilu autora i ekranu
 * pojedynczego posta. Post to ten sam byt niezależnie od miejsca publikacji
 * (backend §8), więc i wygląda tak samo.
 *
 * [canDelete] pokazuje „Usuń" — tylko na własnych postach; moderacja społeczności
 * dostanie osobną ścieżkę.
 */
@Composable
fun PostCard(
	post: Post,
	canDelete: Boolean,
	onToggleLike: () -> Unit,
	onOpenComments: () -> Unit,
	onToggleShare: () -> Unit,
	onDelete: () -> Unit,
	onOpenAuthor: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				// Avatar + nazwa autora prowadzą do jego profilu; kosz zostaje osobno.
				Row(
					modifier = Modifier.weight(1f).clickable(onClick = onOpenAuthor),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Avatar(displayName = post.authorDisplayName, avatarUrl = post.authorAvatarUrl, size = 40.dp)
					Column {
						Text(
							text = post.authorDisplayName,
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.SemiBold,
						)
						Text(
							text = RelativeTime.format(post.createdAt),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				if (canDelete) {
					Text(
						text = "Usuń",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.clickable(onClick = onDelete),
					)
				}
			}
			Text(
				text = post.content,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 6.dp),
			)
			if (!post.imageUrl.isNullOrBlank()) {
				AsyncImage(
					model = post.imageUrl,
					contentDescription = "Zdjęcie w poście",
					contentScale = ContentScale.Crop,
					modifier = Modifier
						.padding(top = 10.dp)
						.fillMaxWidth()
						.heightIn(max = 320.dp)
						.clip(RoundedCornerShape(8.dp)),
				)
			}
			Row(
				horizontalArrangement = Arrangement.spacedBy(20.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(top = 10.dp),
			) {
				Text(
					text = "${if (post.likedByMe) "♥" else "♡"} ${post.likeCount}",
					style = MaterialTheme.typography.labelLarge,
					color = if (post.likedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onToggleLike),
				)
				Text(
					text = "💬 ${post.commentCount}",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onOpenComments),
				)
				Text(
					text = "↗ ${post.shareCount}",
					style = MaterialTheme.typography.labelLarge,
					color = if (post.sharedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onToggleShare),
				)
			}
		}
	}
}

@Composable
fun DeletePostDialog(isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Usunąć post?") },
		text = { Text("Tej operacji nie można cofnąć.") },
		confirmButton = {
			TextButton(
				onClick = onConfirm,
				enabled = !isDeleting,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) {
				Text(if (isDeleting) "Usuwanie…" else "Usuń")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Anuluj") }
		},
	)
}
