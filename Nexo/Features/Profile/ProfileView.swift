//
//  ProfileView.swift
//  Nexo
//

import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var appState
    @State private var profile: PlatformUser?
    @State private var bookings: [GymClass] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showGymSwitcher = false
    @State private var showSignOutConfirm = false

    private var upcomingBookings: [GymClass] {
        bookings.filter { $0.startTime >= Date() }.sorted { $0.startTime < $1.startTime }
    }

    private var pastBookings: [GymClass] {
        bookings.filter { $0.startTime < Date() }.sorted { $0.startTime > $1.startTime }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(profile?.fullName ?? " ")
                                .font(.title2)
                                .fontWeight(.bold)
                            if profile?.role == .admin {
                                Text("Admin")
                                    .font(.caption)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Capsule().fill(Color.accentColor))
                            }
                        }
                        Text(profile?.email ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }

                Section("Gym Membership") {
                    LabeledContent("Gym", value: appState.currentGym?.name ?? "")
                    LabeledContent("Role", value: appState.gymRole.displayName)
                    Button {
                        showGymSwitcher = true
                    } label: {
                        Label("Switch Gym", systemImage: "arrow.left.arrow.right")
                    }
                }

                Section("Upcoming Bookings (\(upcomingBookings.count))") {
                    if isLoading {
                        ProgressView()
                    } else if upcomingBookings.isEmpty {
                        Text("No upcoming bookings. Go to the Schedule tab to book your next class!")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(upcomingBookings) { gymClass in
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
                                    Task { await cancelBooking(gymClass) }
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundStyle(.red)
                                }
                                .buttonStyle(.borderless)
                            }
                            .swipeActions(edge: .trailing) {
                                Button("Cancel", role: .destructive) {
                                    Task { await cancelBooking(gymClass) }
                                }
                            }
                        }
                    }
                }

                Section("Booking History (\(pastBookings.count))") {
                    if pastBookings.isEmpty && !isLoading {
                        Text("No past classes yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(pastBookings) { gymClass in
                            NavigationLink(value: gymClass) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(gymClass.title)
                                        .font(.headline)
                                    Text(gymClass.formattedDateTime)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }

                Section {
                    Button(role: .destructive) {
                        showSignOutConfirm = true
                    } label: {
                        Text("Sign Out")
                            .frame(maxWidth: .infinity)
                    }
                }
            }
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
            .sheet(isPresented: $showGymSwitcher) {
                GymSwitcherSheet()
            }
            .task { await loadData() }
        }
    }

    private func loadData() async {
        guard let uid = FirebaseBackend.shared.currentUID() else { return }
        isLoading = true
        do {
            async let profileResult = FirebaseBackend.shared.fetchUserProfile()
            async let bookingsResult = FirebaseBackend.shared.fetchMemberBookings(gymId: appState.gymId, userId: uid)
            profile = try await profileResult
            bookings = try await bookingsResult
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
}

#Preview {
    ProfileView()
}
