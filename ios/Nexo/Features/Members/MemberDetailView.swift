//
//  MemberDetailView.swift
//  Nexo
//

import SwiftUI

struct MemberDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @State private var viewModel: MemberDetailViewModel
    @State private var classToCancel: GymClass?
    @State private var showGrantPlan = false
    @State private var showRemoveConfirmation = false

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

            if appState.canManageClasses {
                Section("Credit Wallet") {
                    if viewModel.activePlans.isEmpty {
                        Text("No active plans")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.activePlans) { item in
                            WalletItemRow(item: item)
                        }
                        .onDelete { offsets in
                            for index in offsets {
                                let item = viewModel.activePlans[index]
                                Task { await viewModel.revokeActivePlan(item) }
                            }
                        }
                    }
                    Button {
                        showGrantPlan = true
                    } label: {
                        Label("Grant Plan", systemImage: "plus.circle")
                    }
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
                            if appState.canManageClasses {
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

            if appState.isAdmin || appState.gymRole == .owner {
                Section {
                    Button("Remove Member from Gym", role: .destructive) {
                        showRemoveConfirmation = true
                    }
                }
            }
        }
        .navigationTitle(member.name)
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Remove \(member.name) from the gym?",
            isPresented: $showRemoveConfirmation,
            titleVisibility: .visible
        ) {
            Button("Remove Member", role: .destructive) {
                Task {
                    if await viewModel.removeMember() {
                        dismiss()
                    }
                }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This cannot be undone. Their bookings and credit wallet will be removed.")
        }
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
        .sheet(isPresented: $showGrantPlan) {
            GrantPlanSheet(plans: viewModel.availablePlans) { plan, customDate in
                await viewModel.grantPlan(plan, customExpiresAt: customDate)
            }
        }
        .task { await viewModel.loadBookings() }
        .task { await viewModel.loadWallet() }
        .task { await viewModel.loadAvailablePlans() }
    }
}

// MARK: - Wallet Item Row

private struct WalletItemRow: View {
    let item: ActivePlanItem

    private var detailText: String {
        let scope = item.workoutType.map { " (\($0))" } ?? ""
        switch item.type {
        case .unlimited:
            return "Unlimited access\(scope)"
        case .credits:
            return "\(item.remainingCredits) credits remaining\(scope)"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.planName)
                .font(.headline)
            Text(detailText)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Expires \(item.expiresAt.formatted(date: .abbreviated, time: .omitted))")
                .font(.caption2)
                .foregroundStyle(item.isExpired ? .red : .secondary)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Grant Plan Sheet

private struct GrantPlanSheet: View {
    @Environment(\.dismiss) private var dismiss
    let plans: [MembershipPlan]
    let onGrant: (MembershipPlan, Date?) async -> Void

    @State private var useCustomExpiration = false
    @State private var customExpirationDate = Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date()
    @State private var selectedPlan: MembershipPlan?
    @State private var isGranting = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Select Plan Template") {
                    if plans.isEmpty {
                        Text("No plans yet — create one in Manage → Plans first.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(plans) { plan in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(plan.name)
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                        Text(plan.type.shortName)
                                            .font(.caption2)
                                            .fontWeight(.semibold)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(plan.type == .monthly ? Color.blue.opacity(0.15) : Color.orange.opacity(0.15))
                                            .foregroundStyle(plan.type == .monthly ? Color.blue : Color.orange)
                                            .clipShape(Capsule())
                                    }
                                    Text(plan.summary)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selectedPlan?.id == plan.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.blue)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                selectedPlan = plan
                            }
                        }
                    }
                }

                if selectedPlan != nil {
                    Section("Expiration Options") {
                        Toggle("Set Custom Expiration Date", isOn: $useCustomExpiration)

                        if useCustomExpiration {
                            DatePicker(
                                "Expires On",
                                selection: $customExpirationDate,
                                in: Date()...,
                                displayedComponents: .date
                            )
                        } else {
                            Text("Will use default expiration defined by plan components.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Grant Plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Grant") {
                        guard let plan = selectedPlan else { return }
                        isGranting = true
                        Task {
                            await onGrant(plan, useCustomExpiration ? customExpirationDate : nil)
                            dismiss()
                        }
                    }
                    .disabled(selectedPlan == nil || isGranting)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

#Preview {
    NavigationStack {
        MemberDetailView(gymId: UUID(), member: Member(id: "preview-uid", name: "Jane Doe", email: "jane@example.com", joinedAt: Date()))
    }
}

