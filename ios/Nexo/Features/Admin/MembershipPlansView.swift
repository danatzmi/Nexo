//
//  MembershipPlansView.swift
//  Nexo
//

import SwiftUI

struct MembershipPlansView: View {
    @State private var viewModel: MembershipPlansViewModel
    @State private var showingAddPlan = false
    @State private var planToEdit: MembershipPlan?
    @State private var planToDelete: MembershipPlan?

    init(gymId: UUID) {
        _viewModel = State(initialValue: MembershipPlansViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(viewModel.plans.count) Plans")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    showingAddPlan = true
                } label: {
                    Label("Add Plan", systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()

            if viewModel.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.plans.isEmpty {
                ContentUnavailableView(
                    "No Membership Plans",
                    systemImage: "creditcard",
                    description: Text("Create a plan to start granting memberships to your members")
                )
            } else {
                List(viewModel.plans) { plan in
                    PlanRow(plan: plan)
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                planToDelete = plan
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            Button {
                                planToEdit = plan
                            } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            .tint(.blue)
                        }
                }
                .listStyle(.plain)
                .refreshable { await viewModel.loadPlans() }
            }
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .confirmationDialog(
            "Delete \"\(planToDelete?.name ?? "")\"?",
            isPresented: Binding(get: { planToDelete != nil }, set: { if !$0 { planToDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let plan = planToDelete { Task { await viewModel.deletePlan(plan.id) } }
            }
            Button("Cancel", role: .cancel) { planToDelete = nil }
        } message: {
            Text("This cannot be undone. Members who already have this plan granted keep their wallet items.")
        }
        .sheet(isPresented: $showingAddPlan) {
            PlanEditorSheet(planToEdit: nil, viewModel: viewModel)
        }
        .sheet(item: $planToEdit) { plan in
            PlanEditorSheet(planToEdit: plan, viewModel: viewModel)
        }
        .task { await viewModel.loadPlans() }
    }
}

// MARK: - Plan Row

private struct PlanRow: View {
    let plan: MembershipPlan

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name)
                        .font(.headline)
                    Text(plan.type.displayName)
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .foregroundStyle(plan.type == .monthly ? Color.blue : Color.orange)
                }
                Spacer()
                Text(plan.price.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", plan.price) : String(format: "%.2f", plan.price))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(plan.components) { component in
                Text("• \(component.summary)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Plan Editor Sheet

private struct PlanEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    let planToEdit: MembershipPlan?
    let viewModel: MembershipPlansViewModel

    @State private var type: PlanType
    @State private var name: String
    @State private var priceText: String
    @State private var components: [PlanComponent]
    @State private var isSaving = false

    init(planToEdit: MembershipPlan?, viewModel: MembershipPlansViewModel) {
        self.planToEdit = planToEdit
        self.viewModel = viewModel
        let initialType = planToEdit?.type ?? .monthly
        _type = State(initialValue: initialType)
        _name = State(initialValue: planToEdit?.name ?? (initialType == .monthly ? "Monthly Unlimited" : "10-Class Pass"))
        _priceText = State(initialValue: planToEdit.map { String($0.price) } ?? "")
        _components = State(initialValue: planToEdit?.components ?? [
            initialType == .monthly
                ? PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)
                : PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)
        ])
    }

    private var isEditMode: Bool { planToEdit != nil }
    private var price: Double { Double(priceText) ?? 0 }
    private var isValid: Bool { !name.isEmpty && !components.isEmpty && Double(priceText) != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section("Plan Category") {
                    Picker("Category", selection: $type) {
                        Label("Monthly Membership", systemImage: "calendar").tag(PlanType.monthly)
                        Label("Multi-Visit Pass", systemImage: "ticket").tag(PlanType.classPass)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: type) { _, newType in
                        // When creating a new plan, suggest reasonable defaults when switching types
                        if !isEditMode {
                            if name == "Monthly Unlimited" || name == "10-Class Pass" || name.isEmpty {
                                name = newType == .monthly ? "Monthly Unlimited" : "10-Class Pass"
                            }
                            if components.count <= 1 {
                                components = [
                                    newType == .monthly
                                        ? PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)
                                        : PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)
                                ]
                            }
                        }
                    }
                }

                Section("Plan Details") {
                    TextField("Plan Name (e.g. Gold Unlimited, 10-Class Pass)", text: $name)
                        .autocorrectionDisabled()
                    TextField("Price", text: $priceText)
                        .keyboardType(.decimalPad)
                }

                Section {
                    ForEach($components) { $component in
                        ComponentEditor(component: $component)
                    }
                    .onDelete { offsets in
                        components.remove(atOffsets: offsets)
                    }

                    Button {
                        components.append(
                            type == .monthly
                                ? PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)
                                : PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)
                        )
                    } label: {
                        Label("Add Another Component", systemImage: "plus.circle")
                    }
                } header: {
                    Text("Included Access / Components (\(components.count))")
                } footer: {
                    Text("Customize what this plan unlocks. You can add extra components for hybrid plans (e.g. Unlimited CrossFit + 4 Pilates credits).")
                }
            }
            .navigationTitle(isEditMode ? "Edit Plan" : "New Plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditMode ? "Save" : "Create") {
                        isSaving = true
                        let plan = MembershipPlan(
                            id: planToEdit?.id ?? UUID(),
                            name: name,
                            type: type,
                            price: price,
                            components: components
                        )
                        Task {
                            if isEditMode {
                                await viewModel.updatePlan(plan)
                            } else {
                                await viewModel.createPlan(plan)
                            }
                            dismiss()
                        }
                    }
                    .disabled(!isValid || isSaving)
                }
            }
        }
        .presentationDetents([.large])
    }
}

// MARK: - Component Editor

private struct ComponentEditor: View {
    @Environment(AppState.self) private var appState
    @Binding var component: PlanComponent

    private var availableCategories: [String] {
        appState.currentGym?.workoutTypes ?? WorkoutCategory.defaults
    }

    private var isStandardShortcut: Bool {
        component.validityUnit == .months && [1, 2, 3, 6, 12].contains(component.validityValue)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker("Type", selection: $component.type) {
                ForEach(PlanComponentType.allCases, id: \.self) { type in
                    Text(type.displayName).tag(type)
                }
            }
            .pickerStyle(.segmented)

            Picker("Class Type", selection: $component.workoutType) {
                Text("All Classes").tag(String?.none)
                ForEach(availableCategories, id: \.self) { type in
                    Text(type).tag(String?.some(type))
                }
            }

            if component.type == .credits {
                Picker("Credit Reset", selection: $component.resetPeriod) {
                    ForEach(PlanResetPeriod.allCases, id: \.self) { period in
                        Text(period.displayName).tag(period)
                    }
                }
                .pickerStyle(.segmented)

                Stepper(value: $component.creditCount, in: 1...200) {
                    HStack {
                        Text(component.resetPeriod == .monthly ? "Credits / Month:" : "Total Credits:")
                        Spacer()
                        Text("\(component.creditCount)")
                            .fontWeight(.semibold)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Validity Duration")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 6) {
                    ForEach([1, 2, 3, 6, 12], id: \.self) { months in
                        let isSelected = component.validityUnit == .months && component.validityValue == months
                        Button {
                            component.validityUnit = .months
                            component.validityValue = months
                        } label: {
                            Text(months == 12 ? "1 Yr" : "\(months) Mo")
                                .font(.caption2)
                                .fontWeight(isSelected ? .bold : .regular)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(isSelected ? Color.blue : Color(.secondarySystemFill))
                                .foregroundStyle(isSelected ? Color.white : Color.primary)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }

                    let isCustom = !isStandardShortcut
                    Button {
                        if !isCustom {
                            component.validityUnit = .days
                            component.validityValue = 30
                        }
                    } label: {
                        Text("Custom")
                            .font(.caption2)
                            .fontWeight(isCustom ? .bold : .regular)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(isCustom ? Color.blue : Color(.secondarySystemFill))
                            .foregroundStyle(isCustom ? Color.white : Color.primary)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }

                if !isStandardShortcut {
                    HStack {
                        Stepper(value: $component.validityValue, in: 1...365) {
                            Text("Valid for \(component.validityValue)")
                        }
                        Picker("Unit", selection: $component.validityUnit) {
                            ForEach(ValidityUnit.allCases, id: \.self) { unit in
                                Text(unit.displayName).tag(unit)
                            }
                        }
                        .labelsHidden()
                    }
                    .padding(.top, 4)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    NavigationStack {
        MembershipPlansView(gymId: UUID())
    }
}
