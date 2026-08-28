//
//  AddTeamMemberView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct AddTeamMemberView: View {
    enum AddMode: String, CaseIterable {
        case search = "Search"
        case register = "New Account"
    }

    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @State private var addMode: AddMode = .search

    // New Account mode
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var role: UserRole = .coach
    @State private var isLoading = false
    @State private var error: String?

    // Search mode
    @State private var searchQuery = ""
    @State private var allPlatformUsers: [PlatformUser] = []
    @State private var existingTeamMemberIds: Set<String> = []
    @State private var selectedUser: PlatformUser?
    @State private var selectedRole: UserRole = .coach
    @State private var isAddingExisting = false

    var onAdded: () -> Void

    /// Only owners may promote another user to owner — coaches are restricted
    /// to inviting coaches, so the picker (and the ability to submit a
    /// non-coach role) is hidden entirely for them.
    private var canSelectRole: Bool { appState.gymRole == .owner }

    private var matchingUsers: [PlatformUser] {
        guard !searchQuery.isEmpty else { return [] }
        return allPlatformUsers.filter { user in
            !existingTeamMemberIds.contains(user.id) &&
            (user.fullName.localizedCaseInsensitiveContains(searchQuery) ||
             user.email.localizedCaseInsensitiveContains(searchQuery))
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Mode", selection: $addMode) {
                    ForEach(AddMode.allCases, id: \.self) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                switch addMode {
                case .search:
                    searchModeContent
                case .register:
                    registerModeContent
                }
            }
            .navigationTitle("Add Team Member")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .task { await loadSearchData() }
        }
    }

    // MARK: - Search Mode

    private var searchModeContent: some View {
        List {
            if let error {
                Section {
                    Text(error).foregroundStyle(.red).font(.footnote)
                }
            }

            Section {
                if searchQuery.isEmpty {
                    Text("Search by name or email to find an existing user.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if matchingUsers.isEmpty {
                    Text("No matching users found.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(matchingUsers) { user in
                        Button {
                            selectedUser = user
                            selectedRole = .coach
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(user.displayName)
                                        .foregroundStyle(.primary)
                                    Text(user.email)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selectedUser?.id == user.id {
                                    Image(systemName: "checkmark").foregroundStyle(.blue)
                                }
                            }
                        }
                    }
                }
            }

            if let selectedUser {
                Section("Add \(selectedUser.displayName) as") {
                    if canSelectRole {
                        Picker("Role", selection: $selectedRole) {
                            Text(UserRole.coach.displayName).tag(UserRole.coach)
                            Text(UserRole.owner.displayName).tag(UserRole.owner)
                        }
                    }
                    Button {
                        Task { await addExisting(selectedUser) }
                    } label: {
                        HStack {
                            Spacer()
                            if isAddingExisting {
                                ProgressView()
                            } else {
                                Text("Add to Team")
                            }
                            Spacer()
                        }
                    }
                    .disabled(isAddingExisting)
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $searchQuery, prompt: "Search by name or email")
    }

    // MARK: - New Account Mode

    private var registerModeContent: some View {
        ScrollView {
            VStack(spacing: 20) {
                FormCard(title: "Team Member Account") {
                    TextField("First Name", text: $firstName)
                    Divider().opacity(0.5)
                    TextField("Last Name", text: $lastName)
                    Divider().opacity(0.5)
                    TextField("Email", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                    Divider().opacity(0.5)
                    SecureField("Password", text: $password)

                    if canSelectRole {
                        Divider().opacity(0.5)
                        Picker("Role", selection: $role) {
                            Text(UserRole.coach.displayName).tag(UserRole.coach)
                            Text(UserRole.owner.displayName).tag(UserRole.owner)
                        }
                    }
                }

                if let error {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button {
                    Task { await registerNewTeamMember() }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView()
                        } else {
                            Text("Add Team Member").fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .clipShape(Capsule())
                .disabled(!isValid || isLoading)
            }
            .padding(.horizontal)
            .padding(.vertical, 20)
        }
        .background(Color(.systemGroupedBackground))
    }

    private var isValid: Bool {
        !firstName.isEmpty && email.contains("@") && password.count >= 6
    }

    // MARK: - Actions

    private func loadSearchData() async {
        do {
            async let usersResult = FirebaseBackend.shared.fetchAllUsers()
            async let teamResult = FirebaseBackend.shared.fetchTeam(gymId: appState.gymId)
            allPlatformUsers = try await usersResult
            existingTeamMemberIds = Set(try await teamResult.map(\.id))
        } catch {
            self.error = "Error loading users: \(error.localizedDescription)"
        }
    }

    private func addExisting(_ user: PlatformUser) async {
        isAddingExisting = true
        error = nil
        do {
            try await FirebaseBackend.shared.addExistingUserToGym(
                gymId: appState.gymId,
                userId: user.id,
                role: canSelectRole ? selectedRole : .coach
            )
            await MainActor.run {
                onAdded()
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                isAddingExisting = false
            }
        }
    }

    private func registerNewTeamMember() async {
        isLoading = true
        error = nil
        do {
            try await FirebaseBackend.shared.addTeamMember(
                gymId: appState.gymId,
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                email: email.trimmingCharacters(in: .whitespaces),
                password: password,
                role: canSelectRole ? role : .coach
            )
            await MainActor.run {
                onAdded()
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                isLoading = false
            }
        }
    }
}
