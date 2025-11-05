"use client"

import type React from "react"
import { useRef } from "react"

interface UploadAreaProps {
  onFileSelect: (file: File) => void
  selectedFile: File | null
  disabled?: boolean
}

export default function UploadArea({ onFileSelect, selectedFile, disabled }: UploadAreaProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const dragCounter = useRef(0)

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    dragCounter.current++
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    dragCounter.current--
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    dragCounter.current = 0

    const files = e.dataTransfer.files
    if (files.length > 0) {
      onFileSelect(files[0])
    }
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.currentTarget.files
    if (files && files.length > 0) {
      onFileSelect(files[0])
    }
  }

  return (
    <div
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
      onClick={() => !disabled && inputRef.current?.click()}
      className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
        selectedFile ? "border-primary bg-primary/5" : "border-border hover:border-primary hover:bg-primary/5"
      } ${disabled ? "opacity-50 cursor-not-allowed" : ""}`}
    >
      <input
        ref={inputRef}
        type="file"
        onChange={handleInputChange}
        disabled={disabled}
        className="hidden"
        aria-label="Upload file"
      />

      <div className="space-y-2">
        <div className="text-3xl">📂</div>
        <div>
          <p className="text-foreground font-semibold">
            {selectedFile ? selectedFile.name : "Select any file"}
          </p>
          <p className="text-sm text-secondary mt-1">
            {selectedFile ? "Click to change file" : "or drag and drop here"}
          </p>
        </div>
        <p className="text-xs text-muted-foreground mt-3">Supported: All file types</p>
      </div>
    </div>
  )
}
