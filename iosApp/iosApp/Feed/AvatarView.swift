import SwiftUI

struct AvatarView: View {
	let displayName: String
	let avatarUrl: String?
	var size: CGFloat = 40

	var body: some View {
		Group {
			if let avatarUrl, let url = URL(string: avatarUrl) {
				AsyncImage(url: url) { phase in
					if let image = phase.image {
						image.resizable().scaledToFill()
					} else {
						initial
					}
				}
			} else {
				initial
			}
		}
		.frame(width: size, height: size)
		.clipShape(Circle())
		.accessibilityHidden(true)
	}

	private var initial: some View {
		ZStack {
			Circle().fill(Color.accentColor.opacity(0.2))
			Text(displayName.prefix(1).uppercased())
				.font(.system(size: size * 0.4, weight: .semibold))
		}
	}
}
