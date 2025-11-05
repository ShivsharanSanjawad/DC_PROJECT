import { NextRequest, NextResponse } from "next/server"
import fs from "fs"
import path from "path"

// ✅ Use metadata.json from your project root
const META_FILE = path.resolve(process.cwd(), "metadata.json")

export async function GET(request: NextRequest) {
  try {
    const fileName = request.nextUrl.searchParams.get("file")

    if (!fileName) {
      return NextResponse.json({ success: false, message: "No file specified" }, { status: 400 })
    }

    if (!fs.existsSync(META_FILE)) {
      console.error("❌ Metadata file not found at:", META_FILE)
      return NextResponse.json({ success: false, message: "Metadata file missing" }, { status: 404 })
    }

    const metaStore = JSON.parse(fs.readFileSync(META_FILE, "utf8"))
    const fileMeta = metaStore[fileName]

    if (!fileMeta) {
      console.error(`❌ File ${fileName} not found in metadata`)
      return NextResponse.json({ success: false, message: "File metadata not found" }, { status: 404 })
    }

    const downloadApi = `${fileMeta.uploadTo}/api/download`
    const payload = {
      [fileMeta.filename]: {
        filename: fileMeta.filename,
        blocks: fileMeta.blocks,
      },
    }

    // Fetch file from DataNode
    const resp = await fetch(downloadApi, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    })

    if (!resp.ok) {
      const text = await resp.text().catch(() => "")
      console.error("⚠️ Datanode returned error:", resp.status, text)
      return NextResponse.json(
        { success: false, message: `Datanode download failed: ${resp.status} ${text}` },
        { status: 502 }
      )
    }

    const buf = await resp.arrayBuffer()

    return new NextResponse(Buffer.from(buf), {
      headers: {
        "Content-Disposition": `attachment; filename="${fileName}"`,
        "Content-Type": resp.headers.get("content-type") || "application/octet-stream",
      },
    })
  } catch (error) {
    console.error("🚨 Download error:", error)
    return NextResponse.json(
      { success: false, message: "Download failed", error: String(error) },
      { status: 500 }
    )
  }
}
