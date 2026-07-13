//
//  MemberDetailView.swift
//  Nexo
//

import SwiftUI

struct MemberDetailView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: MemberDetailViewModel
    @State private var classToCancel: GymClass?

    private var member: Member { viewModel.member }

    init(gymId: UUID, member: Member) {
        _viewModel = State(initialValue: MemberDetailViewModel(gymId: gymId, member: member))
    }

    var body: some View {
        List {
            Section("Profile") {
                LabeledContent("Name", value: member.name)
                LabeledContent("Email", value: member.email)
                if let joinedAt = member.joinedAt {
                    LabeledContent("Joined", value: joinedAt.formatted(date: .abbreviated, time: .omitted))
                }
            }

            Section("Upcoming Bookings (\(viewModel.upcomingBookings.count))") {
                if viewModel.isLoading {
                    ProgressView()
                } else if viewModel.upcomingBookings.isEmpty {
                    Text("No upcoming bookings")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.upcomingBookings) { gymClass in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(gymClass.title)
                                    .font(.headline)
                                Text(gymClass.formattedDateTime)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if appState.gymRole.canManageClasses {
                                Button(role: .destructive) {
                                    classToCancel = gymClass
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                    }
                }
            }

            Section("Past Classes (\(viewModel.pastBookings.count))") {
                if viewModel.pastBookings.isEmpty && !viewModel.isLoading {
                    Text("No past classes")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.pastBookings) { gymClass in
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
        .navigationTitle(member.name)
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Cancel \"\(classToCancel?.title ?? "")\" for \(member.name)?",
            isPresented: Binding(get: { classToCancel != nil }, set: { if !$0 { classToCancel = nil } }),
            titleVisibility: .visible
        ) {
            Button("Cancel Booking", role: .destructive) {
                if let gymClass = classToCancel {
                    Task {
                        await viewModel.cancelBooking(gymClass)
                        classToCancel = nil
                    }
                }
            }
            Button("Keep Booking", role: .cancel) { classToCancel = nil }
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .task { await viewModel.loadBookings() }
    }
}

#Preview {
    NavigationStack {
        MemberDetailView(gymId: UUID(), member: Member(id: "preview-uid", name: "Jane Doe", email: "jane@example.com", joinedAt: Date()))
    }
}
