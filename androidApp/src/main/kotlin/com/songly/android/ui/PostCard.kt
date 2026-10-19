package com.songly.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
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
 * [canDelete] pokazuje „Usuń" — na własnych postach, a w społeczności także
 * moderatorom (backend pozwala im usunąć dowolny post u siebie).
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
	// `null` = bez „Zgłoś" (własny post albo poza społecznością — zwykłych postów
	// nie ma komu zgłosić, backend §8).
	onReport: (() -> Unit)? = null,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				// Avatar + nazwa autora prowadzą do jego profilu; kosz zostaje osobno.
				Row(
					modifier = Modifier
						.weight(1f)
						.clickable(role = Role.Button, onClickLabel = "Otwórz profil", onClick = onOpenAuthor),
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
				if (onReport != null) {
					Text(
						text = "Zgłoś",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.accessibleAction(description = "Zgłoś post", onClick = onReport),
					)
				}
				if (canDelete) {
					Text(
						text = "Usuń",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.accessibleAction(description = "Usuń post", onClick = onDelete),
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
			// Opis i stan podane wprost, bo sama ikona z liczbą nic czytnikowi nie mówi
			// (wcześniej znaki „♡ 3" czytał jako „biały kier, trzy"). Cel dotyku 48 dp
			// sam robi odstęp od treści, więc rząd nie potrzebuje górnego paddingu.
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				PostAction(
					icon = if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
					count = post.likeCount,
					active = post.likedByMe,
					modifier = Modifier.accessibleAction(
						description = "Polubienia: ${post.likeCount}",
						state = if (post.likedByMe) "polubiony" else "niepolubiony",
						onClick = onToggleLike,
					),
				)
				PostAction(
					icon = Icons.Outlined.ChatBubbleOutline,
					count = post.commentCount,
					active = false,
					modifier = Modifier.accessibleAction(description = "Komentarze: ${post.commentCount}", onClick = onOpenComments),
				)
				PostAction(
					icon = Icons.Filled.Repeat,
					count = post.shareCount,
					active = post.sharedByMe,
					modifier = Modifier.accessibleAction(
						description = "Udostępnienia: ${post.shareCount}",
						state = if (post.sharedByMe) "udostępniony" else "nieudostępniony",
						onClick = onToggleShare,
					),
				)
			}
		}
	}
}

/**
 * Ikona z licznikiem pod postem. Aktywna (polubiony, udostępniony) świeci zielenią
 * — tak jak serce przy polubionym utworze — a nieaktywna jest szara i nie
 * odciąga wzroku od treści.
 */
@Composable
private fun PostAction(icon: ImageVector, count: Long, active: Boolean, modifier: Modifier) {
	val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
	Row(
		modifier = modifier.padding(horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
		Text(text = count.toString(), style = MaterialTheme.typography.labelLarge, color = color)
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
