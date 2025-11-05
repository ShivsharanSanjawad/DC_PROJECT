"use client"

import { useState } from "react"
import UploadPage from "@/components/upload-page"
import BrowserPage from "@/components/browser-page"
import { ToastProvider } from "@/components/toast-provider"

export default function Home() {
  const [currentPage, setCurrentPage] = useState<"upload" | "browser">("upload")
  const [refreshTrigger, setRefreshTrigger] = useState(0)

  const handleUploadSuccess = () => {
    setCurrentPage("browser")
    setRefreshTrigger((prev) => prev + 1)
  }

  return (
    <ToastProvider>
      {currentPage === "upload" ? (
        <UploadPage onUploadSuccess={handleUploadSuccess} />
      ) : (
        <BrowserPage onNavigateToUpload={() => setCurrentPage("upload")} refreshTrigger={refreshTrigger} />
      )}
    </ToastProvider>
  )
}
