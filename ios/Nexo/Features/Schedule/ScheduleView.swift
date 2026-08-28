//
//  ScheduleView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

/// Sophisticated, muted tones for `ClassRow`'s capacity/waitlist text —
/// deliberately desaturated compared to the system `.red`/`.green`/`.orange`,
/// per the app's quiet, premium visual language.
private extension Color {
    static let mutedCrimson = Color(red: 0.68, green: 0.30, blue: 0.32)
    static let mutedSlate = Color(red: 0.42, green: 0.46, blue: 0.51)
    static let mutedAmber = Color(red: 0.78, green: 0.58, blue: 0.28)
}

struct ScheduleView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: ScheduleViewModel
    @State private var weekDates: [Date] = []
    @State private var showDatePicker = false
    @State private var showingAddClass = false
    @State private var showGymSwitcher = false
    @State private var classToCancel: GymClass? = nil
    @State private var showCancelCard = false
    @State private var classToLeaveWaitlist: GymClass? = nil
    @State private var showLeaveWaitlistCard = false
    @State private var showSuccessPopup = false
    @State private var successPopupTitle = "Booked!"
    @State private var successPopupMessage = ""
    @State private var successPopupIcon = "checkmark"
    @State private var successPopupIconColor: Color = .green

    init(gymId: UUID) {
        _viewModel = State(initialValue: ScheduleViewModel(gymId: gymId))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Schedule")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        Button {
                            showGymSwitcher = true
                        } label: {
                            HStack(spacing: 4) {
                                Text(appState.currentGym?.name ?? "")
                                Image(systemName: "chevron.down")
                                    .font(.caption2)
                                    .fontWeight(.semibold)
                            }
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer()
                    HStack(spacing: 16) {
                        if appState.canManageClasses {
                            Button {
                                showingAddClass = true
                            } label: {
                                Image(systemName: "plus")
                                    .font(.title3)
                            }
                        }
                        Button {
                            showDatePicker = true
                        } label: {
                            Image(systemName: "calendar")
                                .font(.title3)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 4)

                WeekDayPicker(weekDates: weekDates, selectedDate: $viewModel.selectedDate)
                    .padding(.vertical, 8)

                Divider()

                if let error = viewModel.errorMessage {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                        .padding()
                }

                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.classes.isEmpty {
                    ContentUnavailableView(
                        "No Classes",
                        systemImage: "calendar.badge.exclamationmark",
                        description: Text("No classes scheduled for \(viewModel.selectedDate.formatted(date: .abbreviated, time: .omitted))")
                    )
                } else {
                    List(viewModel.classes) { gymClass in
                        ClassRow(
                            gymClass: gymClass,
                            isBooked: viewModel.bookedClassIds.contains(gymClass.id),
                            isWaitlisted: viewModel.waitlistedClassIds.contains(gymClass.id),
                            onBook: { Task { await viewModel.bookClass(gymClass) } },
                            onCancel: { classToCancel = gymClass; showCancelCard = true },
                            onJoinWaitlist: { Task { await viewModel.joinWaitlist(gymClass) } },
                            onLeaveWaitlist: { classToLeaveWaitlist = gymClass; showLeaveWaitlistCard = true }
                        )
                        .listRowSeparator(.hidden)
                        .overlay(alignment: .bottom) {
                            Divider().opacity(0.5)
                        }
                    }
                    .navigationDestination(for: GymClass.self) { gymClass in
                        ClassDetailView(gymId: appState.gymId, gymClass: gymClass)
                    }
                    .listStyle(.plain)
                    .refreshable {
                        await viewModel.loadBookingStatus()
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .task {
                generateWeekDates(for: viewModel.selectedDate)
                await viewModel.loadInitialData()
            }
            .onChange(of: viewModel.selectedDate) { _, newDate in
                generateWeekDates(for: newDate)
                viewModel.dateChanged()
            }
            .onDisappear {
                viewModel.stopObserving()
            }
            .alert(
                "Booking",
                isPresented: Binding(get: { viewModel.bookingMessage != nil }, set: { if !$0 { viewModel.bookingMessage = nil } })
            ) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.bookingMessage ?? "")
            }
            .sheet(isPresented: $showDatePicker) {
                DatePickerSheet(selectedDate: $viewModel.selectedDate)
            }
            .sheet(isPresented: $showGymSwitcher) {
                GymSwitcherSheet()
            }
            .sheet(isPresented: $showingAddClass) {
                NavigationStack {
                    AddClassView { }
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Cancel") { showingAddClass = false }
                            }
                        }
                }
            }
            .cancelBookingOverlay(
                isPresented: $showCancelCard,
                classTitle: classToCancel?.title ?? ""
            ) {
                if let gymClass = classToCancel {
                    Task { await viewModel.cancelBooking(gymClass) }
                }
            }
            .leaveWaitlistOverlay(
                isPresented: $showLeaveWaitlistCard,
                classTitle: classToLeaveWaitlist?.title ?? ""
            ) {
                if let gymClass = classToLeaveWaitlist {
                    Task { await viewModel.leaveWaitlist(gymClass) }
                }
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
        }
    }

    // MARK: - Week Dates

    private func generateWeekDates(for date: Date) {
        let calendar = Calendar.current
        guard let weekStart = calendar.dateInterval(of: .weekOfYear, for: date)?.start else { return }
        weekDates = (0..<7).compactMap { offset in
            calendar.date(byAdding: .day, value: offset, to: weekStart)
        }
    }
}

// MARK: - Week Day Picker

private struct WeekDayPicker: View {
    let weekDates: [Date]
    @Binding var selectedDate: Date
    @State private var slideForward = true

    private var monthTitle: String {
        guard let first = weekDates.first, let last = weekDates.last else { return "" }
        let firstMonth = first.formatted(.dateTime.month(.wide).year())
        let lastMonth = last.formatted(.dateTime.month(.wide).year())
        return firstMonth == lastMonth ? firstMonth : "\(first.formatted(.dateTime.month(.abbreviated))) / \(last.formatted(.dateTime.month(.abbreviated).year()))"
    }

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Button { shiftWeek(by: -1) } label: {
                    Image(systemName: "chevron.left")
                        .font(.subheadline.weight(.semibold))
                        .frame(width: 36, height: 36)
                        .background(Color(.systemGray6))
                        .clipShape(Circle())
                }

                Spacer()

                Text(monthTitle)
                    .font(.subheadline)
                    .fontWeight(.semibold)

                Spacer()

                Button { shiftWeek(by: 1) } label: {
                    Image(systemName: "chevron.right")
                        .font(.subheadline.weight(.semibold))
                        .frame(width: 36, height: 36)
                        .background(Color(.systemGray6))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal)

            HStack(spacing: 4) {
                ForEach(weekDates, id: \.self) { date in
                    DayButton(
                        date: date,
                        isSelected: Calendar.current.isDate(date, inSameDayAs: selectedDate),
                        isToday: Calendar.current.isDateInToday(date)
                    ) {
                        selectedDate = date
                    }
                }
            }
            .padding(.horizontal, 8)
            .id(weekDates.first)
            .transition(
                .asymmetric(
                    insertion: .move(edge: slideForward ? .trailing : .leading),
                    removal:   .move(edge: slideForward ? .leading  : .trailing)
                )
            )
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 40, coordinateSpace: .local)
                    .onEnded { value in
                        if value.translation.width < -40 {
                            shiftWeek(by: 1)
                        } else if value.translation.width > 40 {
                            shiftWeek(by: -1)
                        }
                    }
            )
        }
    }

    private func shiftWeek(by weeks: Int) {
        slideForward = weeks > 0
        withAnimation(.easeInOut(duration: 0.25)) {
            if let newDate = Calendar.current.date(byAdding: .weekOfYear, value: weeks, to: selectedDate) {
                selectedDate = newDate
            }
        }
    }
}

// MARK: - Day Button

private struct DayButton: View {
    let date: Date
    let isSelected: Bool
    let isToday: Bool
    let action: () -> Void

    private var dayName: String { date.formatted(.dateTime.weekday(.abbreviated)) }
    private var dayNumber: String { date.formatted(.dateTime.day()) }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(dayName)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundStyle(isSelected ? .white : .secondary)

                Text(dayNumber)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(isSelected ? .white : .primary)
            }
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.accentColor : Color(.systemGray6))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isToday && !isSelected ? Color.accentColor : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Class Row

private struct ClassRow: View {
    let gymClass: GymClass
    let isBooked: Bool
    let isWaitlisted: Bool
    let onBook: () -> Void
    let onCancel: () -> Void
    let onJoinWaitlist: () -> Void
    let onLeaveWaitlist: () -> Void

    private var isPast: Bool { gymClass.startTime < Date() }

    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(value: gymClass) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(gymClass.title)
                            .font(.headline)
                        if isBooked {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                                .font(.caption)
                        } else if isWaitlisted {
                            Image(systemName: "clock.fill")
                                .foregroundStyle(.orange)
                                .font(.caption)
                        }
                        Spacer()
                        Text(gymClass.formattedTime)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Text(gymClass.coach)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        Text("\(gymClass.currentAttendees) / \(gymClass.capacity) booked")
                            .font(.footnote)
                            .foregroundStyle(gymClass.isFull ? Color.mutedCrimson : Color.mutedSlate)
                        if gymClass.waitlistCount > 0 {
                            Text("\(gymClass.waitlistCount) waiting")
                                .font(.footnote)
                                .foregroundStyle(Color.mutedAmber)
                        }
                    }
                }
            }

            if !isPast {
                if isBooked {
                    Button(action: onCancel) {
                        Text("Cancel")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.red)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                } else if gymClass.isFull {
                    if isWaitlisted {
                        Button(action: onLeaveWaitlist) {
                            Text("Leave")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color(.systemGray3))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button(action: onJoinWaitlist) {
                            Text("Waitlist")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color.orange)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    Button(action: onBook) {
                        Text("Book")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.blue)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .swipeActions(edge: .trailing) {
            if !isPast {
                if isBooked {
                    Button("Cancel", role: .destructive, action: onCancel)
                } else if gymClass.isFull {
                    if isWaitlisted {
                        Button("Leave Waitlist", role: .destructive, action: onLeaveWaitlist)
                    } else {
                        Button("Join Waitlist", action: onJoinWaitlist)
                            .tint(.orange)
                    }
                } else {
                    Button("Book", action: onBook)
                        .tint(.blue)
                }
            }
        }
    }
}

// MARK: - Date Picker Sheet

private struct DatePickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedDate: Date

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker(
                    "Select Date",
                    selection: $selectedDate,
                    displayedComponents: [.date]
                )
                .datePickerStyle(.graphical)
                .padding()

                Spacer()
            }
            .navigationTitle("Pick a Date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#Preview {
    ScheduleView(gymId: UUID())
}
