"use client"

import { useState, useEffect } from "react"
import { useToast } from "./toast-provider"
import Header from "./header"
import Breadcrumbs from "./breadcrumbs"
import DirectoryTree from "./directory-tree"
import FileTable from "./file-table"
import { fetchDirectoryListing, type FileSystemItem } from "@/lib/api"

interface BrowserPageProps {
  onNavigateToUpload: () => void
  refreshTrigger: number
}

export default function BrowserPage({ onNavigateToUpload, refreshTrigger }: BrowserPageProps) {
  const [currentPath, setCurrentPath] = useState("/")
  const [items, setItems] = useState<FileSystemItem | null>(null)
  const [rootFolders, setRootFolders] = useState<Array<{ name: string; path: string; count: number }>>([])
  const [loading, setLoading] = useState(true)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const { addToast } = useToast()

  useEffect(() => {
    loadRootFolders()
  }, [])

  useEffect(() => {
    loadDirectory(currentPath)
  }, [currentPath, refreshTrigger])

  const loadRootFolders = async () => {
    try {
      const data = await fetchDirectoryListing("/")
      setRootFolders(data.folders)
    } catch (error) {
      console.error("Failed to load root folders:", error)
    }
  }

  const loadDirectory = async (path: string) => {
    setLoading(true)
    try {
      const data = await fetchDirectoryListing(path)
      setItems(data)
    } catch (error) {
      addToast("Failed to load directory", "error")
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  const handleNavigate = (path: string) => {
    setCurrentPath(path)
  }

  const handleRefresh = () => {
    loadDirectory(currentPath)
    addToast("Directory refreshed", "success")
  }

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <Header onUploadClick={onNavigateToUpload} />

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <div
          className={`${
            sidebarOpen ? "w-64" : "w-0"
          } bg-card border-r border-border transition-all duration-300 overflow-hidden flex flex-col`}
        >
          <div className="p-4 border-b border-border">
            <h2 className="text-sm font-semibold text-foreground">Folders</h2>
          </div>
          <div className="flex-1 overflow-y-auto">
            <DirectoryTree folders={rootFolders} currentPath={currentPath} onNavigate={handleNavigate} />
          </div>
        </div>

        {/* Main Content */}
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="border-b border-border bg-card/50 backdrop-blur-sm">
            <div className="p-4 space-y-4">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setSidebarOpen(!sidebarOpen)}
                  className="p-2 hover:bg-muted rounded-lg transition-colors"
                  aria-label="Toggle sidebar"
                >
                  ☰
                </button>
                <Breadcrumbs path={currentPath} onNavigate={handleNavigate} />
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-6">
            {loading ? (
              <div className="flex items-center justify-center h-full">
                <div className="text-center space-y-3">
                  <div className="inline-block animate-spin">🔄</div>
                  <p className="text-secondary">Loading directory...</p>
                </div>
              </div>
            ) : items ? (
              <FileTable
                path={currentPath}
                files={items.files}
                folders={items.folders}
                onNavigate={handleNavigate}
                onRefresh={handleRefresh}
                onUploadClick={onNavigateToUpload}
              />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
