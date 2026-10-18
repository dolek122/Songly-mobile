import SwiftUI
import Shared

/// Wiersz posta — odpowiednik `PostCard` z Androida (na razie bez komentarzy,
/// usuwania i zgłaszania; te ekrany dojdą razem z resztą iOS).
struct PostRow: View {
	let post: FeedPost
	let onToggleLike: () -> Void
	let onToggleShare: () -> Void

	var body: some View {
		VStack(alignment: .leading, spacing: 8) {
			HStack(spacing: 10) {
				AvatarView(displayName: post.authorDisplayName, avatarUrl: post.authorAvatarUrl, size: 40)
				VStack(alignment: .leading, spacing: 2) {
					Text(post.authorDisplayName)
						.font(.subheadline.weight(.semibold))
					// Ten sam formatter co na Androidzie — „5 min temu" ma brzmieć identycznie.
					Text(RelativeTime.shared.format(isoInstant: post.createdAt))
						.font(.caption)
						.foregroundStyle(.secondary)
				}
			}

			Text(post.content)

			if let imageUrl = post.imageUrl, let url = URL(string: imageUrl) {
				AsyncImage(url: url) { image in
					image.resizable().scaledToFill()
				} placeholder: {
					Color.secondary.opacity(0.1)
				}
				.frame(maxWidth: .infinity)
				.frame(height: 240)
				.clipShape(RoundedRectangle(cornerRadius: 8))
				.accessibilityLabel("Zdjęcie w poście")
			}

			HStack(spacing: 20) {
				Button(action: onToggleLike) {
					Text("\(post.likedByMe ? "♥" : "♡") \(post.likeCount)")
				}
				.foregroundStyle(post.likedByMe ? Color.accentColor : Color.secondary)
				.accessibilityLabel(post.likedByMe ? "Cofnij polubienie" : "Polub")

				Text("💬 \(post.commentCount)")
					.foregroundStyle(.secondary)

				Button(action: onToggleShare) {
					Text("↗ \(post.shareCount)")
				}
				.foregroundStyle(post.sharedByMe ? Color.accentColor : Color.secondary)
				.accessibilityLabel(post.sharedByMe ? "Cofnij udostępnienie" : "Udostępnij")
			}
			.font(.callout)
			// Bez tego dotknięcie w dowolnym miejscu wiersza `List` uruchamia wszystkie
			// przyciski naraz.
			.buttonStyle(.borderless)
		}
		.padding(.vertical, 6)
	}
}
