//
//  GymHomeView.swift
//  Nexo
//

import SwiftUI

struct GymHomeView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: GymHomeViewModel
    @State private var showCreateClass = false
    let gym: Gym

    init(gym: Gym) {
        self.gym = gym
        _viewModel = State(initialValue: GymHomeViewModel(gymId: gym.id))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    gymBannerCard

                    if viewModel.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    } else if appState.isAdmin || appState.gymRole == .owner {
                        ownerDashboard
                    } else if appState.gymRole == .coach {
                        coachDashboard
                    } else {
                        memberDashboard
                    }
                }
                .padding(.vertical)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Home")
            .navigationBarTitleDisplayMode(.inline)
            .alert(
                "Error",
                isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
            ) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .sheet(isPresented: $showCreateClass) {
                NavigationStack {
                    AddClassView { Task { await loadForRole() } }
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Cancel") { showCreateClass = false }
                            }
                        }
                }
            }
            .navigationDestination(for: GymClass.self) { gymClass in
                ClassDetailView(gymId: gym.id, gymClass: gymClass)
            }
            .task { await loadForRole() }
            .refreshable { await loadForRole() }
        }
    }

    private func loadForRole() async {
        if appState.isAdmin || appState.gymRole == .owner {
            await viewModel.loadOwnerData()
        } else if appState.gymRole == .coach {
            await viewModel.loadCoachData()
        } else {
            await viewModel.loadMemberData()
        }
    }

    // MARK: - Header

    private var greeting: String {
        let name = viewModel.userDisplayName
        if appState.isAdmin || appState.gymRole == .owner {
            return name.isEmpty ? "Welcome back!" : "\(name), welcome back!"
        } else if appState.gymRole == .coach {
            return name.isEmpty ? "Welcome back, Coach!" : "Coach \(name), welcome back!"
        } else {
            return name.isEmpty ? "Ready for your workout?" : "\(name), ready for your workout?"
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(greeting)
                .font(.title2)
                .fontWeight(.bold)
            Text(Date(), style: .date)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
    }

    private var gymBannerCard: some View {
        Menu {
            ForEach(appState.myGyms, id: \.gym.id) { entry in
                Button {
                    appState.enter(gym: entry.gym, role: entry.role)
                } label: {
                    Text(entry.gym.name)
                }
            }
            Divider()
            Button {
                appState.leaveGym()
            } label: {
                Label(
                    appState.isAdmin ? "Platform Dashboard" : "Leave Gym",
                    systemImage: appState.isAdmin ? "rectangle.stack" : "arrow.left.arrow.right"
                )
            }
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 48, height: 48)
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                }

                HStack(spacing: 4) {
                    Text(gym.name)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(.primary)
                    Image(systemName: "chevron.down")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                if appState.gymRole != .member {
                    Text(appState.gymRole.displayName)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .overlay(Capsule().stroke(Color.secondary.opacity(0.3), lineWidth: 0.5))
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
            .shadow(color: .black.opacity(0.04), radius: 10, y: 4)
        }
        .buttonStyle(.plain)
        .padding(.horizontal)
    }

    // MARK: - Owner / Admin

    private var ownerDashboard: some View {
        VStack(spacing: 16) {
            scoreboardCard
                .padding(.horizontal)

            Button {
                showCreateClass = true
            } label: {
                Label("Create New Class", systemImage: "plus.circle.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .clipShape(Capsule())
            .padding(.horizontal)
        }
    }

    private var scoreboardCard: some View {
        HStack(spacing: 0) {
            ScoreboardMetric(title: "Classes Today", value: "\(viewModel.todayClasses.count)", icon: "calendar")
            Divider()
                .padding(.vertical, 16)
            ScoreboardMetric(title: "Bookings Today", value: "\(viewModel.totalBookingsToday)", icon: "checkmark.circle")
        }
        .hairlineCard()
    }

    // MARK: - Coach

    private var coachDashboard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your Schedule Today")
                .font(.headline)
                .padding(.horizontal)

            if viewModel.todayClasses.isEmpty {
                ContentUnavailableView(
                    "No Classes Today",
                    systemImage: "calendar.badge.checkmark",
                    description: Text("No classes assigned to you today")
                )
            } else {
                VStack(spacing: 12) {
                    ForEach(viewModel.todayClasses) { gymClass in
                        NavigationLink(value: gymClass) {
                            ClassScheduleCard(gymClass: gymClass)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal)
            }
        }
    }

    // MARK: - Member

    private var memberDashboard: some View {
        VStack(spacing: 16) {
            NextBookingCard(booking: viewModel.nextBooking) {
                appState.selectedTab = 1
            }
            .padding(.horizontal)

            myPlansHomeCard
                .padding(.horizontal)
        }
    }

    private var myPlansHomeCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("MY PLANS")
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .tracking(0.5)

            if viewModel.activePlans.isEmpty {
                Text("No active plans. Contact your gym to purchase a membership plan.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 12) {
                    ForEach(Array(viewModel.activePlans.enumerated()), id: \.element.id) { index, plan in
                        if index > 0 { Divider().opacity(0.5) }
                        PlanHomeRow(plan: plan)
                    }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .hairlineCard()
    }
}

// MARK: - Scoreboard Metric

/// One half of the owner/admin scoreboard card — no background/border of its
/// own, since it's a segment inside `scoreboardCard`'s single hairline card,
/// separated from its sibling by a plain `Divider()`.
private struct ScoreboardMetric: View {
    let title: String
    let value: String
    let icon: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(size: 32, weight: .thin))
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }
}

// MARK: - Class Schedule Card (Coach)

private struct ClassScheduleCard: View {
    let gymClass: GymClass

    var body: some View {
        HStack(spacing: 16) {
            Text(gymClass.formattedTime)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .overlay(Capsule().stroke(Color.secondary.opacity(0.3), lineWidth: 0.5))

            VStack(alignment: .leading, spacing: 6) {
                Text(gymClass.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                ProgressView(value: Double(gymClass.currentAttendees), total: Double(max(gymClass.capacity, 1)))
                    .tint(.secondary)
                Text("\(gymClass.currentAttendees) / \(gymClass.capacity) booked")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding()
        .hairlineCard()
    }
}

// MARK: - Plan Row (Member)

/// One row in `myPlansHomeCard` — plan name, access detail (unlimited vs.
/// remaining credits), and an Active/Expired status pill with the
/// expiration date.
private struct PlanHomeRow: View {
    let plan: ActivePlanItem

    private var accessDetail: String {
        let scope = plan.workoutType.map { " (\($0))" } ?? ""
        switch plan.type {
        case .unlimited:
            return "Unlimited access\(scope)"
        case .credits:
            if plan.resetPeriod == .monthly {
                let avail = plan.availableCredits()
                let resets = plan.currentCycleBounds().end.formatted(date: .abbreviated, time: .omitted)
                return "\(avail) of \(plan.creditCount) credits remaining\(scope) · Resets \(resets)"
            } else {
                return "\(plan.remainingCredits) credits remaining\(scope)"
            }
        }
    }

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(plan.planName)
                    .font(.headline)
                Text(accessDetail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Expires \(plan.expiresAt.formatted(date: .abbreviated, time: .omitted))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text(plan.isExpired ? "Expired" : "Active")
                .font(.caption2)
                .fontWeight(.semibold)
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(plan.isExpired ? Color.red : Color.green, in: Capsule())
        }
    }
}

// MARK: - Next Booking Card (Member)

private struct NextBookingCard: View {
    let booking: GymClass?
    let onBookTapped: () -> Void

    var body: some View {
        if let booking {
            NavigationLink(value: booking) {
                HStack(spacing: 0) {
                    Rectangle()
                        .fill(Color.indigo)
                        .frame(width: 4)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Next Booking")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(.secondary)
                        Text(booking.title)
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundStyle(.primary)
                        Label(booking.formattedDateTime, systemImage: "clock")
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                        Label(booking.coach, systemImage: "person")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .hairlineCard()
            }
            .buttonStyle(.plain)
        } else {
            VStack(spacing: 12) {
                Image(systemName: "figure.run.circle")
                    .font(.system(size: 40))
                    .foregroundStyle(.secondary)
                Text("No upcoming classes.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Button(action: onBookTapped) {
                    Text("Book a Class")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .clipShape(Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding()
            .hairlineCard()
        }
    }
}

#Preview {
    GymHomeView(gym: Gym(name: "CrossFit Example", ownerUID: "preview-uid"))
}
