"use client"

interface BreadcrumbsProps {
  path: string
  onNavigate: (path: string) => void
}

export default function Breadcrumbs({ path, onNavigate }: BreadcrumbsProps) {
  const parts = path.split("/").filter(Boolean)

  return (
    <nav className="flex items-center gap-1 text-sm" aria-label="Breadcrumb">
      <button
        onClick={() => onNavigate("/")}
        className={`px-2 py-1 rounded hover:bg-muted transition-colors ${
          path === "/" ? "text-primary font-semibold" : "text-secondary hover:text-foreground"
        }`}
      >
        /
      </button>

      {parts.map((part, index) => {
        const fullPath = "/" + parts.slice(0, index + 1).join("/")
        return (
          <div key={fullPath} className="flex items-center gap-1">
            <span className="text-muted">/</span>
            <button
              onClick={() => onNavigate(fullPath)}
              className={`px-2 py-1 rounded hover:bg-muted transition-colors ${
                fullPath === path ? "text-primary font-semibold" : "text-secondary hover:text-foreground"
              }`}
            >
              {part}
            </button>
          </div>
        )
      })}
    </nav>
  )
}
