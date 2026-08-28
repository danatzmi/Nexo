import { useState } from 'react'
import { ChartBar, Settings } from 'lucide-react'
import { useAuth } from '../context/useAuth'
import ClassesTab from '../components/manage/ClassesTab'
import MembersTab from '../components/manage/MembersTab'
import TeamTab from '../components/manage/TeamTab'
import PlansTab from '../components/manage/PlansTab'
import GymSettingsModal from '../components/manage/GymSettingsModal'

type ManageTab = 'classes' | 'members' | 'team' | 'plans' | 'reports'

const tabs: { id: ManageTab; label: string }[] = [
  { id: 'classes', label: 'Classes' },
  { id: 'members', label: 'Members' },
  { id: 'team', label: 'Team' },
  { id: 'plans', label: 'Plans' },
  { id: 'reports', label: 'Reports' },
]

// Unified admin surface — a five-segment picker (Classes/Members/Team/Plans/
// Reports) over the tab components in `components/manage/`, plus a
// gear-icon-triggered gym settings modal, gated to owners (and platform
// admins). Mirrors `AdminView.swift`'s `AdminTab` enum, except Team is its
// own top-level segment here rather than nested under Members as on iOS.
export default function Manage() {
  const { currentGym, gymRole, isAdmin } = useAuth()
  const [activeTab, setActiveTab] = useState<ManageTab>('classes')
  const [showSettings, setShowSettings] = useState(false)

  const canManageGym = gymRole === 'owner' || isAdmin

  if (!currentGym) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6 text-center">
        <p className="text-sm text-neutral-400">Select a gym on Home to manage it.</p>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex items-center justify-between gap-3 px-6 pt-6 md:px-10 md:pt-10">
        <div className="flex flex-1 gap-1 overflow-x-auto rounded-full bg-white/5 p-1">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`shrink-0 rounded-full px-4 py-2 text-xs font-semibold transition-colors ${
                activeTab === tab.id
                  ? 'bg-blue-600 text-white'
                  : 'text-neutral-400 hover:text-white'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {canManageGym && (
          <button
            type="button"
            onClick={() => setShowSettings(true)}
            className="shrink-0 rounded-full p-2.5 text-neutral-400 transition-colors hover:bg-white/5 hover:text-white"
          >
            <Settings size={18} />
          </button>
        )}
      </div>

      {activeTab === 'classes' && <ClassesTab gymId={currentGym.id} />}
      {activeTab === 'members' && <MembersTab gymId={currentGym.id} />}
      {activeTab === 'team' && <TeamTab gymId={currentGym.id} />}
      {activeTab === 'plans' && <PlansTab gymId={currentGym.id} />}
      {activeTab === 'reports' && (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <ChartBar size={32} className="text-neutral-600" />
          <p className="text-sm text-neutral-400">Analytics coming soon.</p>
        </div>
      )}

      {showSettings && (
        <GymSettingsModal gym={currentGym} onClose={() => setShowSettings(false)} />
      )}
    </div>
  )
}
