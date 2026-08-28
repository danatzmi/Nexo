//
//  AddMemberView.swift
//  Nexo
//

import SwiftUI

struct AddMemberView: View {
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
    @State private var isLoading = false
    @State private var error: String?

    // Search mode
    @State private var searchQuery = ""
    @State private var allPlatformUsers: [PlatformUser] = []
    @State private var existingMemberIds: Set<String> = []
    @State private var addingUserId: String?

    var onAdded: () -> Void

    private var matchingUsers: [PlatformUser] {
        guard !searchQuery.isEmpty else { return [] }
        return allPlatformUsers.filter { user in
            !existingMemberIds.contains(user.id) &&
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
            .navigationTitle("Add Member")
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
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(user.displayName)
                                    .font(.body)
                                Text(user.email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("Add to Gym") {
                                Task { await addExisting(user) }
                            }
                            .buttonStyle(.bordered)
                            .disabled(addingUserId == user.id)
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .searchable(text: $searchQuery, prompt: "Search by name or email")
    }

    // MARK: - New Account Mode

    private var registerModeContent: some View {
        Form {
            Section("Member Account") {
                TextField("First Name", text: $firstName)
                TextField("Last Name", text: $lastName)
                TextField("Email", text: $email)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                    .autocorrectionDisabled()
                SecureField("Password", text: $password)
            }

            if let error {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
            }

            Section {
                Button("Add Member") {
                    Task { await registerNewMember() }
                }
                .frame(maxWidth: .infinity)
                .disabled(!isValid || isLoading)

                if isLoading { ProgressView().frame(maxWidth: .infinity) }
            }
        }
    }

    private var isValid: Bool {
        !firstName.isEmpty && email.contains("@") && password.count >= 6
    }

    // MARK: - Actions

    private func loadSearchData() async {
        do {
            async let usersResult = FirebaseBackend.shared.fetchAllUsers()
            async let membersResult = FirebaseBackend.shared.fetchMembers(gymId: appState.gymId)
            allPlatformUsers = try await usersResult
            existingMemberIds = Set(try await membersResult.map(\.id))
        } catch {
            self.error = "Error loading users: \(error.localizedDescription)"
        }
    }

    private func addExisting(_ user: PlatformUser) async {
        addingUserId = user.id
        error = nil
        do {
            try await FirebaseBackend.shared.addExistingUserToGym(gymId: appState.gymId, userId: user.id, role: .member)
            await MainActor.run {
                onAdded()
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                addingUserId = nil
            }
        }
    }

    private func registerNewMember() async {
        isLoading = true
        error = nil
        do {
            try await FirebaseBackend.shared.addMember(
                gymId: appState.gymId,
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                email: email.trimmingCharacters(in: .whitespaces),
                password: password
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
