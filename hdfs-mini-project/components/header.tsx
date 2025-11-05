"use client"

interface HeaderProps {
  onUploadClick: () => void
}

export default function Header({ onUploadClick }: HeaderProps) {
  return (
    <header className="bg-card border-b border-border">
      <div className="px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">HDFS Mini Project</h1>
          <p className="text-xs text-secondary">File Management System</p>
        </div>
        <button
          onClick={onUploadClick}
          className="bg-primary hover:bg-primary/90 text-primary-foreground font-semibold px-4 py-2 rounded-lg transition-colors"
        >
          + Upload File
        </button>
      </div>
    </header>
  )
}
