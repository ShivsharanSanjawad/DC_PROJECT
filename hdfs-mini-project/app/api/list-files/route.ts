import { type NextRequest, NextResponse } from "next/server"

export async function GET(_request: NextRequest) {
  try {
    // Directly point to Namenode
    const namenodeUrl = "http://localhost:9002/api/list-files"

    // Fetch the list of files from Namenode
    const resp = await fetch(namenodeUrl, { cache: "no-store" })

    if (!resp.ok) {
      const text = await resp.text().catch(() => "")
      return NextResponse.json(
        { success: false, message: `Namenode list-files failed: ${text}` },
        { status: 502 }
      )
    }

    // Parse and normalize response
    const files = await resp.json()

    // Optional: sort files by most recent or largest size
    const sortedFiles = Array.isArray(files)
      ? files.sort((a, b) => b.filesize - a.filesize)
      : []

    // Return consistent JSON structure for your UI
    return NextResponse.json({
      success: true,
      count: sortedFiles.length,
      files: sortedFiles,
    })
  } catch (error) {
    console.error("List-files error:", error)
    return NextResponse.json(
      { success: false, message: "Failed to list files", error: String(error) },
      { status: 500 }
    )
  }
}
