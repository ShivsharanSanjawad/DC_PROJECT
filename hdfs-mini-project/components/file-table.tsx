"use client"

import { useState, useMemo } from "react"
import { useToast } from "./toast-provider"
import { downloadFile } from "@/lib/api"

interface File {
  name: string
  path: string
  size: number
  modified: string
}

interface Folder {
  name: string
  path: string
  count: number
}

interface FileTableProps {
  path: string
  files: File[]
  folders: Folder[]
  onNavigate: (path: string) => void
  onRefresh: () => void
  onUploadClick: () => void
}

type SortKey = "name" | "size" | "modified"
type SortOrder = "asc" | "desc"

export default function FileTable({ path, files, folders, onNavigate, onRefresh, onUploadClick }: FileTableProps) {
  const [searchQuery, setSearchQuery] = useState("")
  const [sortKey, setSortKey] = useState<SortKey>("name")
  const [sortOrder, setSortOrder] = useState<SortOrder>("asc")
  const [currentPage, setCurrentPage] = useState(1)
  const [downloading, setDownloading] = useState<string | null>(null)
  const { addToast } = useToast()

  const itemsPerPage = 50

  const allItems = useMemo(() => {
    const combined = [
      ...folders.map((f) => ({ ...f, type: "folder" as const })),
      ...files.map((f) => ({ ...f, type: "file" as const })),
    ]

    // Filter by search
    const filtered = combined.filter((item) => item.name.toLowerCase().includes(searchQuery.toLowerCase()))

    // Sort
    const sorted = [...filtered].sort((a, b) => {
      let aVal: any = a[sortKey]
      let bVal: any = b[sortKey]

      if (sortKey === "size" && a.type === "folder") aVal = 0
      if (sortKey === "size" && b.type === "folder") bVal = 0

      if (typeof aVal === "string") {
        aVal = aVal.toLowerCase()
        bVal = bVal.toLowerCase()
      }

      const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0
      return sortOrder === "asc" ? comparison : -comparison
    })

    return sorted
  }, [folders, files, searchQuery, sortKey, sortOrder])

  const paginatedItems = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage
    return allItems.slice(start, start + itemsPerPage)
  }, [allItems, currentPage])

  const totalPages = Math.ceil(allItems.length / itemsPerPage)

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortOrder(sortOrder === "asc" ? "desc" : "asc")
    } else {
      setSortKey(key)
      setSortOrder("asc")
    }
    setCurrentPage(1)
  }

  const handleDownload = async (filePath: string, fileName: string) => {
    setDownloading(filePath)
    try {
      await downloadFile(filePath, fileName)
      addToast(`Downloaded "${fileName}"`, "success")
    } catch (error) {
      addToast("Download failed", "error")
      console.error(error)
    } finally {
      setDownloading(null)
    }
  }

  const formatSize = (bytes: number) => {
    if (bytes === 0) return "-"
    const k = 1024
    const sizes = ["B", "KB", "MB", "GB"]
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i]
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    })
  }

  const SortIcon = ({ column }: { column: SortKey }) => {
    if (sortKey !== column) return <span className="text-muted">⇅</span>
    return sortOrder === "asc" ? <span className="text-primary">↑</span> : <span className="text-primary">↓</span>
  }

  if (allItems.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-center space-y-4">
        <div className="text-5xl">📭</div>
        <div>
          <h3 className="text-lg font-semibold text-foreground">This folder is empty</h3>
          <p className="text-sm text-secondary mt-1">Upload a file to get started</p>
        </div>
        <button
          onClick={onUploadClick}
          className="bg-primary hover:bg-primary/90 text-primary-foreground font-semibold px-4 py-2 rounded-lg transition-colors"
        >
          Upload File
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="flex flex-col md:flex-row gap-4 items-start md:items-center justify-between">
        <input
          type="text"
          placeholder="Search files and folders..."
          value={searchQuery}
          onChange={(e) => {
            setSearchQuery(e.target.value)
            setCurrentPage(1)
          }}
          className="flex-1 bg-card border border-border rounded-lg px-4 py-2 text-foreground placeholder-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          aria-label="Search files"
        />
        <button
          onClick={onRefresh}
          className="bg-muted hover:bg-muted/80 text-foreground font-semibold px-4 py-2 rounded-lg transition-colors whitespace-nowrap"
        >
          Refresh
        </button>
      </div>

      {/* Table */}
      <div className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-muted/30 border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left font-semibold text-foreground">
                  <button
                    onClick={() => handleSort("name")}
                    className="flex items-center gap-2 hover:text-primary transition-colors"
                  >
                    Name <SortIcon column="name" />
                  </button>
                </th>
                <th className="px-4 py-3 text-right font-semibold text-foreground hidden md:table-cell">
                  <button
                    onClick={() => handleSort("size")}
                    className="flex items-center justify-end gap-2 hover:text-primary transition-colors ml-auto"
                  >
                    Size <SortIcon column="size" />
                  </button>
                </th>
                <th className="px-4 py-3 text-right font-semibold text-foreground hidden lg:table-cell">
                  <button
                    onClick={() => handleSort("modified")}
                    className="flex items-center justify-end gap-2 hover:text-primary transition-colors ml-auto"
                  >
                    Modified <SortIcon column="modified" />
                  </button>
                </th>
                <th className="px-4 py-3 text-right font-semibold text-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {paginatedItems.map((item) => (
                <tr key={item.path} className="border-b border-border hover:bg-muted/20 transition-colors">
                  <td className="px-4 py-3">
                    {item.type === "folder" ? (
                      <button
                        onClick={() => onNavigate(item.path)}
                        className="flex items-center gap-2 text-primary hover:underline"
                      >
                        <span>Folder</span>
                        <span className="font-medium">{item.name}</span>
                      </button>
                    ) : (
                      <div className="flex items-center gap-2 text-foreground">
                        <span>File</span>
                        <span>{item.name}</span>
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right text-secondary hidden md:table-cell">
                    {item.type === "file" ? formatSize(item.size) : "-"}
                  </td>
                  <td className="px-4 py-3 text-right text-secondary hidden lg:table-cell">
                    {item.type === "file" ? formatDate(item.modified) : "-"}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {item.type === "file" && (
                      <button
                        onClick={() => handleDownload(item.path, item.name)}
                        disabled={downloading === item.path}
                        className="text-primary hover:text-primary/80 disabled:text-muted font-semibold transition-colors"
                        aria-label={`Download ${item.name}`}
                      >
                        {downloading === item.path ? "Downloading..." : "Download"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-secondary">
            Showing {(currentPage - 1) * itemsPerPage + 1} to {Math.min(currentPage * itemsPerPage, allItems.length)} of{" "}
            {allItems.length}
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
              className="px-3 py-1 bg-muted hover:bg-muted/80 disabled:opacity-50 disabled:cursor-not-allowed rounded transition-colors"
            >
              ← Prev
            </button>
            <span className="px-3 py-1 text-foreground font-semibold">
              {currentPage} / {totalPages}
            </span>
            <button
              onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
              className="px-3 py-1 bg-muted hover:bg-muted/80 disabled:opacity-50 disabled:cursor-not-allowed rounded transition-colors"
            >
              Next →
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
