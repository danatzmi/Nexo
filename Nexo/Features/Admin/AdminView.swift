//
//  AdminView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct AdminView: View {
    @Environment(AppState.self) private var appState
    @State private var selectedTab: AdminTab = .classes
    
    enum AdminTab: String, CaseIterable {
        case classes = "Classes"
        case workouts = "Workouts"
        case members = "Members"
        case reports = "Reports"
        
        var icon: String {
            switch self {
            case .classes: return "calendar"
            case .workouts: return "figure.strengthtraining.traditional"
            case .members: return "person.2"
            case .reports: return "chart.bar"
            }
        }
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab Picker
                Picker("Admin Section", selection: $selectedTab) {
                    ForEach(AdminTab.allCases, id: \.self) { tab in
                        Label(tab.rawValue, systemImage: tab.icon)
                            .tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding()
                
                // Content based on selected tab
                switch selectedTab {
                case .classes:
                    ClassesAdminView()
                case .workouts:
                    WorkoutsLibraryView(gymId: appState.gymId)
                case .members:
                    MembersTabView()
                case .reports:
                    PlaceholderView(title: "Reports", icon: "chart.bar", message: "Analytics coming soon")
                }
            }
            .navigationTitle("Admin Tools")
        }
    }
}

// MARK: - Classes Admin View

private struct ClassesAdminView: View {
    @Environment(AppState.self) private var appState
    @State private var allClasses: [GymClass] = []
    @State private var isLoadingClasses = false
    @State private var errorMessage: String?
    @State private var showingAddClass = false
    @State private var classToEdit: GymClass?
    @State private var classToDelete: GymClass?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(allClasses.count) Classes")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    showingAddClass = true
                } label: {
                    Label("Add Class", systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()

            if isLoadingClasses {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if allClasses.isEmpty {
                ContentUnavailableView(
                    "No Classes Yet",
                    systemImage: "calendar.badge.plus",
                    description: Text("Create your first class to get started")
                )
            } else {
                List(allClasses) { gymClass in
                    NavigationLink {
                        ClassDetailView(gymId: appState.gymId, gymClass: gymClass)
                    } label: {
                        AdminClassCard(gymClass: gymClass)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            classToDelete = gymClass
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        Button {
                            classToEdit = gymClass
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        .tint(.blue)
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadAllClasses() }
            }
        }
        .sheet(isPresented: $showingAddClass) {
            NavigationStack {
                AddClassView { Task { await loadAllClasses() } }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showingAddClass = false }
                    }
                }
            }
        }
        .sheet(item: $classToEdit) { gymClass in
            NavigationStack {
                AddClassView(gymClass: gymClass) { Task { await loadAllClasses() } }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { classToEdit = nil }
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete \"\(classToDelete?.title ?? "")\"?",
            isPresented: Binding(get: { classToDelete != nil }, set: { if !$0 { classToDelete = nil } }),
            titleVisibility: .visible
        ) {
            if classToDelete?.seriesId != nil {
                Button("Delete This Class Only", role: .destructive) {
                    if let gymClass = classToDelete { Task { await deleteClass(gymClass) } }
                }
                Button("Delete This & Future Classes", role: .destructive) {
                    if let gymClass = classToDelete { Task { await deleteSeries(gymClass) } }
                }
            } else {
                Button("Delete", role: .destructive) {
                    if let gymClass = classToDelete { Task { await deleteClass(gymClass) } }
                }
            }
            Button("Cancel", role: .cancel) { classToDelete = nil }
        } message: {
            Text(classToDelete?.seriesId != nil ? "This is part of a recurring series." : "This cannot be undone.")
        }
        .task { await loadAllClasses() }
    }

    private func loadAllClasses() async {
        isLoadingClasses = true
        do {
            let fetched = try await FirebaseBackend.shared.fetchAllClasses(gymId: appState.gymId)
            allClasses = fetched.sorted { $0.startTime < $1.startTime }
        } catch {
            errorMessage = "Error loading classes: \(error.localizedDescription)"
        }
        isLoadingClasses = false
    }

    private func deleteClass(_ gymClass: GymClass) async {
        do {
            try await FirebaseBackend.shared.deleteClass(gymId: appState.gymId, classId: gymClass.id)
            allClasses.removeAll { $0.id == gymClass.id }
        } catch {
            errorMessage = "Failed to delete: \(error.localizedDescription)"
        }
        classToDelete = nil
    }

    private func deleteSeries(_ gymClass: GymClass) async {
        guard let seriesId = gymClass.seriesId else { return }
        do {
            try await FirebaseBackend.shared.deleteClassSeries(gymId: appState.gymId, seriesId: seriesId, from: gymClass.startTime)
            allClasses.removeAll { $0.seriesId == seriesId && $0.startTime >= gymClass.startTime }
        } catch {
            errorMessage = "Failed to delete series: \(error.localizedDescription)"
        }
        classToDelete = nil
    }
}

// MARK: - Admin Class Card Component

private struct AdminClassCard: View {
    let gymClass: GymClass
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(gymClass.title)
                    .font(.headline)
                Spacer()
                Text(gymClass.formattedTime)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            
            HStack {
                Label(gymClass.coach, systemImage: "person.fill")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Text(gymClass.formattedDateTime.replacingOccurrences(of: gymClass.formattedTime, with: "").trimmingCharacters(in: .whitespaces))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            
            HStack {
                Label("\(gymClass.durationMinutes) min", systemImage: "clock")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                
                Spacer()
                
                HStack(spacing: 4) {
                    Image(systemName: "person.2.fill")
                    Text("\(gymClass.currentAttendees)/\(gymClass.capacity)")
                }
                .font(.caption)
                .foregroundStyle(gymClass.isFull ? .red : .green)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    Capsule()
                        .fill(gymClass.isFull ? Color.red.opacity(0.1) : Color.green.opacity(0.1))
                )
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Members Tab View

private struct MembersTabView: View {
    @Environment(AppState.self) private var appState

    private enum SubTab: String, CaseIterable {
        case members = "Members"
        case team = "Team"
    }

    @State private var subTab: SubTab = .members

    var body: some View {
        VStack(spacing: 0) {
            Picker("Members Section", selection: $subTab) {
                ForEach(SubTab.allCases, id: \.self) { tab in
                    Text(tab.rawValue).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            switch subTab {
            case .members:
                GymMembersView(gymId: appState.gymId)
            case .team:
                TeamView()
            }
        }
    }
}

// MARK: - Team View

private struct TeamView: View {
    @Environment(AppState.self) private var appState
    @State private var team: [TeamMember] = []
    @State private var isLoading = false
    @State private var showingAddCoach = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(team.count) Members")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                if appState.gymRole.canManageClasses {
                    Button {
                        showingAddCoach = true
                    } label: {
                        Label("Add Coach", systemImage: "plus")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()

            if isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if team.isEmpty {
                ContentUnavailableView(
                    "No Team Members",
                    systemImage: "person.2",
                    description: Text("Add coaches to get started")
                )
            } else {
                List(team) { member in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(member.fullName)
                                .font(.headline)
                            Text(member.email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(member.role.displayName)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(Color(.systemGray5)))
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadTeam() }
            }
        }
        .task { await loadTeam() }
        .sheet(isPresented: $showingAddCoach) {
            AddCoachView { Task { await loadTeam() } }
        }
    }

    private func loadTeam() async {
        isLoading = true
        do {
            team = try await FirebaseBackend.shared.fetchTeam(gymId: appState.gymId)
        } catch {
            print("Error loading team: \(error)")
        }
        isLoading = false
    }
}

// MARK: - Placeholder View

private struct PlaceholderView: View {
    let title: String
    let icon: String
    let message: String
    
    var body: some View {
        ContentUnavailableView(
            title,
            systemImage: icon,
            description: Text(message)
        )
    }
}

#Preview {
    AdminView()
}
