"use client"

import { useContext } from "react"
import { ToastContext } from "./toast-provider"

export default function Toast() {
  const context = useContext(ToastContext)
  if (!context) return null

  const { toasts, removeToast } = context

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`px-4 py-3 rounded-lg text-sm font-medium animate-in fade-in slide-in-from-bottom-4 duration-300 flex items-center justify-between gap-3 ${
            toast.type === "success"
              ? "bg-green-900/20 text-green-200 border border-green-800"
              : toast.type === "error"
                ? "bg-red-900/20 text-red-200 border border-red-800"
                : "bg-blue-900/20 text-blue-200 border border-blue-800"
          }`}
        >
          <span>{toast.message}</span>
          <button onClick={() => removeToast(toast.id)} className="text-lg leading-none opacity-70 hover:opacity-100">
            ×
          </button>
        </div>
      ))}
    </div>
  )
}
