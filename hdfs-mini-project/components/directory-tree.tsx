"use client"

import { useState } from "react"

interface Folder {
  name: string
  path: string
  count: number
}

interface DirectoryTreeProps {
  folders: Folder[]
  currentPath: string
  onNavigate: (path: string) => void
}

export default function DirectoryTree({ folders, currentPath, onNavigate }: DirectoryTreeProps) {
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set(["/"]))

  const toggleFolder = (path: string) => {
    const newExpanded = new Set(expandedFolders)
    if (newExpanded.has(path)) {
      newExpanded.delete(path)
    } else {
      newExpanded.add(path)
    }
    setExpandedFolders(newExpanded)
  }

  const renderFolders = (items: Folder[], level = 0) => {
    return items.map((folder) => (
      <div key={folder.path}>
        <button
          onClick={() => onNavigate(folder.path)}
          className={`w-full text-left px-4 py-2 text-sm flex items-center gap-2 hover:bg-muted transition-colors ${
            currentPath === folder.path ? "bg-primary/10 text-primary font-semibold" : "text-foreground"
          }`}
          style={{ paddingLeft: `${12 + level * 12}px` }}
        >
          <span className="text-muted-foreground">📁</span>
          <span className="flex-1 truncate">{folder.name}</span>
          <span className="text-xs text-muted-foreground">{folder.count}</span>
        </button>
      </div>
    ))
  }

  return (
    <div className="py-2">
      <button
        onClick={() => onNavigate("/")}
        className={`w-full text-left px-4 py-2 text-sm flex items-center gap-2 hover:bg-muted transition-colors ${
          currentPath === "/" ? "bg-primary/10 text-primary font-semibold" : "text-foreground"
        }`}
      >
        <span className="text-muted-foreground">📁</span>
        <span>Root</span>
      </button>
      {folders && folders.length > 0 ? (
        renderFolders(folders)
      ) : (
        <div className="px-4 py-2 text-xs text-muted-foreground">No folders</div>
      )}
    </div>
  )
}
