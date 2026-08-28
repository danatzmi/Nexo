//
//  ProfileView.swift
//  Nexo
//

import SwiftUI
import PhotosUI
import UIKit

struct ProfileView: View {
    @Environment(AppState.self) private var appState
    @State private var profile: PlatformUser?
    @State private var bookings: [GymClass] = []
    @State private var activePlans: [ActivePlanItem] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showGymSwitcher = false
    @State private var showSignOutConfirm = false
    @State private var classToCancel: GymClass? = nil
    @State private var showCancelCard = false
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var isUploadingPhoto = false

    private var upcomingBookings: [GymClass] {
        bookings.filter { $0.startTime >= Date() }.sorted { $0.startTime < $1.startTime }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    profileHeader

                    if appState.currentGym != nil {
                        gymMembershipCard

                        if appState.gymRole == .member {
                            myPlansCard
                        }

                        upcomingBookingsCard
                    }

                    signOutButton
                }
                .padding(.horizontal)
                .padding(.vertical, 20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Profile")
            .navigationDestination(for: GymClass.self) { gymClass in
                ClassDetailView(gymId: appState.gymId, gymClass: gymClass)
            }
            .refreshable { await loadData() }
            .alert(
                "Error",
                isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })
            ) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage ?? "")
            }
            .alert("Sign Out", isPresented: $showSignOutConfirm) {
                Button("Sign Out", role: .destructive) { appState.signOut() }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Are you sure you want to sign out?")
            }
            .cancelBookingOverlay(isPresented: $showCancelCard, classTitle: classToCancel?.title ?? "") {
                if let gymClass = classToCancel {
                    Task { await cancelBooking(gymClass) }
                }
            }
            .sheet(isPresented: $showGymSwitcher) {
                GymSwitcherSheet()
            }
            .task { await loadData() }
        }
    }

    // MARK: - Header

    private var profileHeader: some View {
        HStack(spacing: 14) {
            // The avatar itself is left untouched by a button — tapping it
            // uses `AvatarView`'s own built-in fullscreen preview. Changing
            // the photo is a separate, explicit affordance (the small
            // camera badge), so the two tap targets don't compete on the
            // same view.
            ZStack(alignment: .bottomTrailing) {
                AvatarView(name: profile?.fullName ?? "?", base64: profile?.profilePicBase64, size: 64)
                if isUploadingPhoto {
                    Circle()
                        .fill(.black.opacity(0.35))
                        .frame(width: 64, height: 64)
                    ProgressView()
                        .tint(.white)
                } else {
                    PhotosPicker(selection: $photoPickerItem, matching: .images) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(6)
                            .background(Color.accentColor, in: Circle())
                            .overlay(Circle().stroke(Color(.systemGroupedBackground), lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                }
            }
            .disabled(isUploadingPhoto)

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(profile?.fullName ?? " ")
                        .font(.title2)
                        .fontWeight(.bold)
                    if profile?.role == .admin {
                        Text("Admin")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .overlay(Capsule().stroke(Color.secondary.opacity(0.3), lineWidth: 0.5))
                    }
                }
                Text(profile?.email ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .onChange(of: photoPickerItem) { _, newItem in
            guard let newItem else { return }
            Task { await uploadProfilePicture(newItem) }
        }
    }

    // MARK: - Gym Membership

    private var gymMembershipCard: some View {
        FormCard(title: "Gym Membership") {
            LabeledContent("Gym", value: appState.currentGym?.name ?? "")
            if appState.gymRole != .member {
                Divider().opacity(0.5)
                LabeledContent("Role", value: appState.gymRole.displayName)
            }
            Divider().opacity(0.5)
            Button {
                showGymSwitcher = true
            } label: {
                Label("Switch Gym", systemImage: "arrow.left.arrow.right")
            }
        }
    }

    // MARK: - My Plans

    private var myPlansCard: some View {
        FormCard(title: "My Plans") {
            if activePlans.isEmpty {
                Text("No active plans (Contact gym to join)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(activePlans.enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider().opacity(0.5) }
                    VStack(alignment: .leading, spacing: 4) {
                        let scope = item.workoutType.map { " (\($0))" } ?? ""
                        if item.type == .unlimited {
                            Text("Unlimited access\(scope)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else if item.resetPeriod == .monthly {
                            let avail = item.availableCredits()
                            let resets = item.currentCycleBounds().end.formatted(date: .abbreviated, time: .omitted)
                            Text("\(avail) of \(item.creditCount) credits remaining\(scope) · Resets \(resets)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("\(item.remainingCredits) credits remaining\(scope)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("Expires \(item.expiresAt.formatted(date: .abbreviated, time: .omitted))")
                            .font(.caption2)
                            .foregroundStyle(item.isExpired ? .red : .secondary)
                    }
                }
            }
        }
    }

    // MARK: - Upcoming Bookings

    private var upcomingBookingsCard: some View {
        FormCard(title: "Upcoming Bookings (\(upcomingBookings.count))") {
            if isLoading {
                ProgressView()
            } else if upcomingBookings.isEmpty {
                Text("No upcoming bookings. Go to the Schedule tab to book your next class!")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(upcomingBookings.enumerated()), id: \.element.id) { index, gymClass in
                    if index > 0 { Divider().opacity(0.5) }
                    HStack {
                        NavigationLink(value: gymClass) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(gymClass.title)
                                    .font(.headline)
                                Text(gymClass.formattedDateTime)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button {
                            classToCancel = gymClass
                            showCancelCard = true
                        } label: {
                            Image(systemName: "xmark.circle")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.borderless)
                    }
                }
            }
        }
    }

    // MARK: - Sign Out

    private var signOutButton: some View {
        Button(role: .destructive) {
            showSignOutConfirm = true
        } label: {
            Text("Sign Out")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.bordered)
        .clipShape(Capsule())
    }

    // MARK: - Data

    private func loadData() async {
        guard let uid = FirebaseBackend.shared.currentUID() else { return }
        isLoading = true
        do {
            profile = try await FirebaseBackend.shared.fetchUserProfile()
            if let gym = appState.currentGym {
                async let bookingsResult = FirebaseBackend.shared.fetchMemberBookings(gymId: gym.id, userId: uid)
                async let activePlansResult = FirebaseBackend.shared.fetchActivePlans(gymId: gym.id, userId: uid)
                bookings = try await bookingsResult
                activePlans = try await activePlansResult
            }
        } catch {
            errorMessage = "Error loading profile: \(error.localizedDescription)"
        }
        isLoading = false
    }

    private func cancelBooking(_ gymClass: GymClass) async {
        do {
            try await FirebaseBackend.shared.cancelBooking(gymId: appState.gymId, classId: gymClass.id)
            bookings.removeAll { $0.id == gymClass.id }
        } catch {
            errorMessage = "Failed to cancel: \(error.localizedDescription)"
        }
    }

    // MARK: - Profile Picture

    private func uploadProfilePicture(_ item: PhotosPickerItem) async {
        isUploadingPhoto = true
        defer { isUploadingPhoto = false; photoPickerItem = nil }

        do {
            guard let data = try await item.loadTransferable(type: Data.self),
                  let uiImage = UIImage(data: data) else { return }

            // Downscale before compressing — quality 0.3 alone can't
            // reliably keep a full-resolution photo-library image under
            // ~30KB, but it can for a small square thumbnail.
            let thumbnail = uiImage.resized(maxDimension: 300)
            guard let compressed = thumbnail.jpegData(compressionQuality: 0.3) else { return }

            let base64 = compressed.base64EncodedString()
            try await FirebaseBackend.shared.updateProfilePicture(base64String: base64)
            profile?.profilePicBase64 = base64
        } catch {
            errorMessage = "Failed to update profile picture: \(error.localizedDescription)"
        }
    }
}

private extension UIImage {
    /// Downscales so the longer side is at most `maxDimension`, preserving
    /// aspect ratio. See `uploadProfilePicture` for why this precedes JPEG
    /// compression rather than relying on `compressionQuality` alone.
    func resized(maxDimension: CGFloat) -> UIImage {
        let longerSide = max(size.width, size.height)
        guard longerSide > maxDimension else { return self }
        let scale = maxDimension / longerSide
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: newSize)) }
    }
}

#Preview {
    ProfileView()
}
