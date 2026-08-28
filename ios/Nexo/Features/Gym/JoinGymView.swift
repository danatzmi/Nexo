//
//  JoinGymView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct JoinGymView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var availableGyms: [Gym] = []
    @State private var searchText = ""
    @State private var isLoading = false
    @State private var joiningGymId: UUID?
    @State private var showCodeSheet = false
    @State private var errorMessage: String?

    var onJoined: (Gym) -> Void

    private var filteredGyms: [Gym] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return availableGyms }
        return availableGyms.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            ($0.city?.localizedCaseInsensitiveContains(query) ?? false) ||
            ($0.joinCode?.localizedCaseInsensitiveContains(query) ?? false)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && availableGyms.isEmpty {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        // Enter Code Card Header
                        Section {
                            Button {
                                showCodeSheet = true
                            } label: {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(Color.accentColor.opacity(0.15))
                                            .frame(width: 40, height: 40)
                                        Image(systemName: "ticket.fill")
                                            .foregroundStyle(Color.accentColor)
                                            .font(.headline)
                                    }

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Have a Gym Code?")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.primary)
                                        Text("Enter your coach's code to join directly")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }

                                    Spacer()

                                    Text("Enter Code")
                                        .font(.caption.weight(.semibold))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Capsule().fill(Color.accentColor.opacity(0.15)))
                                        .foregroundStyle(Color.accentColor)
                                }
                                .padding(.vertical, 4)
                            }
                            .buttonStyle(.plain)
                        }

                        // Directory List
                        Section(searchText.isEmpty ? "All Gyms" : "Results") {
                            if filteredGyms.isEmpty {
                                VStack(spacing: 12) {
                                    Text("No gyms found matching \"\(searchText)\"")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                        .multilineTextAlignment(.center)

                                    Button {
                                        showCodeSheet = true
                                    } label: {
                                        Text("Try Entering a Join Code")
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(Color.accentColor)
                                    }
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 24)
                            } else {
                                ForEach(filteredGyms) { gym in
                                    gymRow(gym)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Find Your Gym")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(
                text: $searchText,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Search by gym name or city"
            )
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(isPresented: $showCodeSheet) {
                EnterJoinCodeSheet { gym in
                    onJoined(gym)
                    dismiss()
                }
            }
            .task {
                await loadGyms()
            }
        }
    }

    // MARK: - Gym Row

    @ViewBuilder
    private func gymRow(_ gym: Gym) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(gym.name)
                    .font(.headline)
                    .foregroundStyle(.primary)

                if let city = gym.city, !city.isEmpty {
                    Label(city, systemImage: "mappin.and.ellipse")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !gym.workoutTypes.isEmpty {
                    Text(gym.workoutTypes.prefix(3).joined(separator: " • "))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            if joiningGymId == gym.id {
                ProgressView()
            } else {
                Button("Join") {
                    Task { await joinGymDirectly(gym) }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - Actions

    private func joinGymDirectly(_ gym: Gym) async {
        joiningGymId = gym.id
        errorMessage = nil
        do {
            try await FirebaseBackend.shared.joinGym(gymId: gym.id)
            await MainActor.run {
                onJoined(gym)
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
                self.joiningGymId = nil
            }
        }
    }

    private func loadGyms() async {
        isLoading = true
        do {
            availableGyms = try await FirebaseBackend.shared.fetchAvailableGyms()
        } catch {
            print("Error loading gyms: \(error)")
        }
        isLoading = false
    }
}

// MARK: - Fallback Join Code Sheet

struct EnterJoinCodeSheet: View {
    @Environment(\.dismiss) private var dismiss

    @State private var code = ""
    @State private var foundGym: Gym?
    @State private var isSearching = false
    @State private var isJoining = false
    @State private var errorMessage: String?

    var onJoined: (Gym) -> Void

    private var cleanCode: String {
        code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                VStack(spacing: 8) {
                    Image(systemName: "ticket")
                        .font(.system(size: 40))
                        .foregroundStyle(Color.accentColor)
                        .padding(.top, 16)

                    Text("Enter Join Code")
                        .font(.title3.bold())

                    Text("Enter the join code provided by your gym.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                VStack(spacing: 12) {
                    TextField("e.g. IRON99", text: $code)
                        .font(.system(size: 28, weight: .heavy, design: .monospaced))
                        .multilineTextAlignment(.center)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .padding(.vertical, 12)
                        .padding(.horizontal, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color(uiColor: .secondarySystemBackground))
                        )
                        .padding(.horizontal, 24)
                        .onChange(of: code) { _, newValue in
                            errorMessage = nil
                            let clean = newValue.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                            if clean.count >= 4 {
                                Task { await lookupCode(clean) }
                            } else {
                                foundGym = nil
                            }
                        }

                    if isSearching {
                        ProgressView().scaleEffect(0.8)
                    } else if let gym = foundGym {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(gym.name)
                                    .font(.headline)
                                if let city = gym.city, !city.isEmpty {
                                    Text(city).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                        }
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.green.opacity(0.1))
                                .stroke(Color.green.opacity(0.3), lineWidth: 1)
                        )
                        .padding(.horizontal, 24)
                    }
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                Button {
                    Task { await submitCode() }
                } label: {
                    if isJoining {
                        ProgressView().tint(.white).frame(maxWidth: .infinity)
                    } else {
                        Text("Join Gym")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 14)
                .background(cleanCode.isEmpty || isJoining ? Color.secondary.opacity(0.3) : Color.accentColor)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .disabled(cleanCode.isEmpty || isJoining)
                .padding(.horizontal, 24)

                Spacer()
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func lookupCode(_ code: String) async {
        isSearching = true
        do {
            foundGym = try await FirebaseBackend.shared.fetchGymByJoinCode(code: code)
        } catch {
            foundGym = nil
        }
        isSearching = false
    }

    private func submitCode() async {
        isJoining = true
        errorMessage = nil
        do {
            let gym = try await FirebaseBackend.shared.joinGymByCode(code: cleanCode)
            await MainActor.run {
                onJoined(gym)
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
                self.isJoining = false
            }
        }
    }
}
