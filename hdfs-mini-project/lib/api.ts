// ---------------------------------------------------------------------------
// ✅ Distributed Storage Integration (No Mock Data)
// ---------------------------------------------------------------------------

export interface FileSystemItem {
  path: string
  folders: Array<{ name: string; path: string; count: number }>
  files: Array<{ name: string; path: string; size: number; modified: string }>
  canWrite: boolean
}

// ---------------------------------------------------------------------------
// ✅ Fetch directory listing from Namenode via Next.js API
// ---------------------------------------------------------------------------
export async function fetchDirectoryListing(path: string): Promise<FileSystemItem> {
  try {
    // Your Next.js backend route → calls http://localhost:9001/api/list-files internally
    const response = await fetch("/api/list-files", { cache: "no-store" })
    if (!response.ok) throw new Error("Failed to fetch file list")

    const data = await response.json()
    if (!data.success) throw new Error(data.message || "List-files failed")

    // Transform backend JSON → into FileSystemItem format
    const files = data.files.map((f: any) => ({
      name: f.filename,
      path: `/${f.filename}`,
      size: f.filesize,
      modified: new Date().toISOString(), // Namenode currently doesn’t return timestamps
    }))

    // Folder support (optional, empty for now)
    const folders: Array<{ name: string; path: string; count: number }> = []

    return {
      path,
      folders,
      files,
      canWrite: true,
    }
  } catch (error) {
    console.error("❌ fetchDirectoryListing error:", error)
    throw new Error("Failed to load directory contents")
  }
}

// ---------------------------------------------------------------------------
// ✅ Download file from backend (which calls Datanode using stored metadata)
// ---------------------------------------------------------------------------
export async function downloadFile(filePath: string, fileName: string): Promise<void> {
  try {
    // Trigger download via Next.js download API (internally uses metadata.json)
    const response = await fetch(`/api/download?file=${encodeURIComponent(fileName)}`)
    if (!response.ok) throw new Error("Download failed")

    // Convert response to a Blob (binary data)
    const blob = await response.blob()

    // Create temporary download link
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (error) {
    console.error("❌ downloadFile error:", error)
    throw new Error("File download failed")
  }
}
