"use client"

import { useState } from "react"
import { useToast } from "./toast-provider"
import UploadArea from "./upload-area"
import ProgressBar from "./progress-bar"

interface UploadPageProps {
  onUploadSuccess: () => void
}

export default function UploadPage({ onUploadSuccess }: UploadPageProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const { addToast } = useToast()

  const handleFileSelect = (selectedFile: File) => {
    setFile(selectedFile)
  }
  

  const handleUpload = async () => {
    if (!file) return

    setUploading(true)
    setProgress(0)

    try {
      const formData = new FormData()
      formData.append("file", file)

      // Simulate progress
      const progressInterval = setInterval(() => {
        setProgress((prev) => {
          if (prev >= 90) return prev
          return prev + Math.random() * 30
        })
      }, 200)

      const response = await fetch("/api/upload", {
        method: "POST",
        body: formData,
      })

      clearInterval(progressInterval)
      setProgress(100)

      if (!response.ok) {
        const error = await response.json()
        throw new Error(error.message || "Upload failed")
      }

      const data = await response.json()
      addToast(`File "${file.name}" uploaded successfully`, "success")

      // Delay before redirect for visual feedback
      setTimeout(() => {
        setFile(null)
        setProgress(0)
        onUploadSuccess()
      }, 500)
    } catch (error) {
      addToast(error instanceof Error ? error.message : "Upload failed", "error")
      setProgress(0)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-bold text-foreground mb-2">HDFS Mini Project</h1>
          <p className="text-secondary">Upload a file to get started</p>
        </div>

        <div className="bg-card border border-border rounded-lg p-8 space-y-6">
          <UploadArea onFileSelect={handleFileSelect} selectedFile={file} disabled={uploading} />

          {file && (
            <div className="bg-muted/20 rounded-lg p-4 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-secondary">File:</span>
                <span className="text-sm font-medium text-foreground">{file.name}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-secondary">Size:</span>
                <span className="text-sm font-medium text-foreground">{(file.size / 1024).toFixed(2)} KB</span>
              </div>
            </div>
          )}

          {uploading && <ProgressBar progress={progress} />}

          <button
            onClick={handleUpload}
            disabled={!file || uploading}
            className="w-full bg-primary hover:bg-primary/90 disabled:bg-muted disabled:cursor-not-allowed text-primary-foreground font-semibold py-3 rounded-lg transition-colors"
          >
            {uploading ? `Uploading... ${Math.round(progress)}%` : "Upload File"}
          </button>
        </div>
      </div>
    </div>
  )
}
