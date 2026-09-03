//
//  ClassDetailView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct ClassDetailView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: ClassDetailViewModel
    @State private var showEdit = false
    @State private var showDeleteConfirm = false
    @State private var showCancelCard = false
    @State private var showLeaveWaitlistCard = false
    @State private var showSuccessPopup = false
    @State private var successPopupTitle = "Booked!"
    @State private var successPopupMessage = ""
    @State private var successPopupIcon = "checkmark"
    @State private var successPopupIconColor: Color = .green

    private var gymClass: GymClass { viewModel.gymClass }

    private var showsFloatingActionButton: Bool {
        gymClass.startTime >= Date()
    }

    init(gymId: UUID, gymClass: GymClass) {
        _viewModel = State(initialValue: ClassDetailViewModel(gymId: gymId, gymClass: gymClass))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                dateTimeCard

                if !gymClass.description.isEmpty {
                    workoutCard
                }

                attendeesCard
            }
            .padding(.horizontal)
            .padding(.top, 20)
            .padding(.bottom, showsFloatingActionButton ? 100 : 20)
        }
        .background(Color(.systemGroupedBackground))
        .overlay(alignment: .bottom) {
            if showsFloatingActionButton {
                memberActionButton
                    .padding(.horizontal)
                    .padding(.bottom, 16)
            }
        }
        .navigationTitle("Class Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if appState.canManageClasses {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button { showEdit = true } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        Button("Delete", role: .destructive) {
                            showDeleteConfirm = true
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
        .sheet(isPresented: $showEdit) {
            NavigationStack {
                AddClassView(gymClass: gymClass) { }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showEdit = false }
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete \"\(gymClass.title)\"?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            if gymClass.seriesId != nil {
                Button("Delete This Class Only", role: .destructive) { Task { await viewModel.deleteClass() } }
                Button("Delete This & Future Classes", role: .destructive) { Task { await viewModel.deleteSeries() } }
            } else {
                Button("Delete", role: .destructive) { Task { await viewModel.deleteClass() } }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text(gymClass.seriesId != nil ? "This is part of a recurring series." : "This cannot be undone.")
        }
        .cancelBookingOverlay(isPresented: $showCancelCard, classTitle: gymClass.title) {
            Task { await viewModel.cancelBooking() }
        }
        .leaveWaitlistOverlay(isPresented: $showLeaveWaitlistCard, classTitle: gymClass.title) {
            Task { await viewModel.leaveWaitlist() }
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.bookingMessage != nil }, set: { if !$0 { viewModel.bookingMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.bookingMessage ?? "")
        }
        .onChange(of: viewModel.didDelete) { _, didDelete in
            if didDelete { dismiss() }
        }
        .onChange(of: viewModel.bookingSuccessMessage) { _, newValue in
            guard let message = newValue else { return }
            viewModel.bookingSuccessMessage = nil
            successPopupTitle = "Booked!"
            successPopupIcon = "checkmark"
            successPopupIconColor = .green
            successPopupMessage = message
            showSuccessPopup = true
        }
        .onChange(of: viewModel.waitlistSuccessMessage) { _, newValue in
            guard let message = newValue else { return }
            viewModel.waitlistSuccessMessage = nil
            successPopupTitle = "Waitlisted!"
            successPopupIcon = "clock.fill"
            successPopupIconColor = .orange
            successPopupMessage = message
            showSuccessPopup = true
        }
        .bookingSuccessOverlay(
            isPresented: $showSuccessPopup,
            title: successPopupTitle,
            message: successPopupMessage,
            iconName: successPopupIcon,
            iconColor: successPopupIconColor
        )
        .task { await viewModel.loadAttendees() }
        .task { await viewModel.loadBookingStatus() }
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(gymClass.title)
                .font(.largeTitle)
                .fontWeight(.bold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Class Info Card

    private var formattedFullDate: String {
        gymClass.startTime.formatted(.dateTime.weekday(.wide).month(.wide).day().year())
    }

    private var formattedTimeRange: String {
        let endTime = gymClass.startTime.addingTimeInterval(TimeInterval(gymClass.durationMinutes * 60))
        return "\(gymClass.startTime.formatted(date: .omitted, time: .shortened)) - \(endTime.formatted(date: .omitted, time: .shortened))"
    }

    private var formattedCoach: String {
        gymClass.coach.isEmpty ? "Unassigned" : gymClass.coach
    }

    private var dateTimeCard: some View {
        VStack(spacing: 0) {
            InfoRow(icon: "calendar", label: "Date", value: formattedFullDate)
            Divider().padding(.leading, 44)
            InfoRow(icon: "clock", label: "Time", value: formattedTimeRange)
            Divider().padding(.leading, 44)
            InfoRow(icon: "person.fill", label: "Coach", value: formattedCoach)
        }
        .hairlineCard()
    }

    // MARK: - Workout Program Card

    private var workoutCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Workout Details")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(0.5)
            Text(gymClass.description)
                .font(.body)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(.systemGray4), lineWidth: 1)
        )
    }

    // MARK: - Attendees

    private var attendeesCardTitle: String {
        let spots = "\(gymClass.currentAttendees)/\(gymClass.capacity)"
        var title = "Attendees: (\(spots))"

        if gymClass.waitlistCount > 0 {
            let waitlist = viewModel.isWaitlisted
                ? "\(viewModel.waitlistPosition ?? 0)/\(gymClass.waitlistCount)"
                : "\(gymClass.waitlistCount)"
            title += " · Waitlist: (\(waitlist))"
        }

        if appState.canManageClasses {
            title += " · Checked In: (\(viewModel.checkedInCount)/\(gymClass.currentAttendees))"
        }

        return title
    }

    private var attendeesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(attendeesCardTitle)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(0.5)

            if viewModel.isLoadingAttendees {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            } else if viewModel.attendees.isEmpty {
                Text("No attendees yet")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 14) {
                    ForEach(viewModel.attendees) { attendee in
                        HStack(spacing: 12) {
                            AvatarView(name: attendee.name, base64: attendee.profilePicBase64, size: 40)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(attendee.name)
                                    .font(.body.weight(.medium))
                                if let checkedInAt = attendee.checkedInAt, attendee.isCheckedIn {
                                    Text("Checked in at \(checkedInAt.formatted(date: .omitted, time: .shortened))")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }

                            Spacer()

                            if appState.canManageClasses {
                                Button {
                                    Task { await viewModel.toggleAttendance(for: attendee) }
                                } label: {
                                    if attendee.isCheckedIn {
                                        Label("Checked In", systemImage: "checkmark.circle.fill")
                                            .font(.caption.bold())
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(Capsule().fill(Color.green.opacity(0.15)))
                                            .foregroundStyle(.green)
                                    } else {
                                        Label("Check In", systemImage: "circle")
                                            .font(.caption.weight(.medium))
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(Capsule().stroke(Color.secondary.opacity(0.3), lineWidth: 1))
                                            .foregroundStyle(.primary)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .hairlineCard()
    }

    // MARK: - Floating Action Button

    /// nil when this member can book (or bypasses the check as staff);
    /// otherwise the reason shown under a dimmed "Book Class" button.
    private var bookingBlockedReason: String? {
        appState.canManageClasses ? nil : viewModel.bookingBlockedReason
    }

    @ViewBuilder
    private var memberActionButton: some View {
        if viewModel.isBooked {
            actionCapsule(title: "Cancel Booking", colors: [.red, .orange.opacity(0.85)]) {
                showCancelCard = true
            }
        } else if viewModel.isWaitlisted {
            actionCapsule(title: "Leave Waitlist", colors: [Color(.systemGray3), Color(.systemGray2)], foreground: .primary) {
                showLeaveWaitlistCard = true
            }
        } else if gymClass.isFull {
            actionCapsule(title: "Join Waitlist", colors: [.orange, .yellow.opacity(0.85)]) {
                Task { await viewModel.joinWaitlist() }
            }
        } else if let reason = bookingBlockedReason {
            VStack(spacing: 6) {
                capsuleLabel("Book Class", foreground: .white, colors: [.blue, .cyan])
                    .opacity(0.45)
                Text(reason)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } else {
            actionCapsule(title: "Book Class", colors: [.blue, .cyan]) {
                Task { await viewModel.book() }
            }
        }
    }

    private func capsuleLabel(_ title: String, foreground: Color, colors: [Color]) -> some View {
        Text(title)
            .fontWeight(.semibold)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .foregroundStyle(foreground)
            .background(LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing))
            .clipShape(Capsule())
            .shadow(color: (colors.first ?? .clear).opacity(0.35), radius: 16, y: 6)
    }

    private func actionCapsule(title: String, colors: [Color], foreground: Color = .white, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            capsuleLabel(title, foreground: foreground, colors: colors)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Info Row

private struct InfoRow: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 20)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline)
                .fontWeight(.medium)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
    }
}

#Preview {
    NavigationStack {
        ClassDetailView(
            gymId: UUID(),
            gymClass: GymClass(
                title: "Morning HIIT",
                coach: "Alex",
                startTime: Date(),
                durationMinutes: 60,
                capacity: 12,
                currentAttendees: 8
            )
        )
    }
}
