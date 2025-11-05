import { type NextRequest, NextResponse } from "next/server"
import fs from "fs"
import path from "path"

const META_FILE = path.resolve(process.cwd(), "metadata.json")

export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData()
    const file = formData.get("file") as File

    if (!file) {
      return NextResponse.json({ success: false, message: "No file provided" }, { status: 400 })
    }

    // Step 1️⃣ — Send file to Namenode
    const namenodeUrl = "http://localhost:9002/api/upload"
    const namenodeForm = new FormData()
    namenodeForm.append("file", file)

    const namenodeResp = await fetch(namenodeUrl, {
      method: "POST",
      body: namenodeForm,
    })

    if (!namenodeResp.ok) {
      const text = await namenodeResp.text().catch(() => "")
      return NextResponse.json(
        { success: false, message: `Namenode upload failed: ${text}` },
        { status: 502 }
      )
    }

    const namenodeData = await namenodeResp.json()
    const uploadTo = namenodeData.upload_to
    const filename = namenodeData.file
    const blocks = namenodeData.blocks

    // Step 2️⃣ — Build metadata
    const metadata = {
      [filename]: { filename, blocks },
    }

    // Step 3️⃣ — Upload file + metadata to Datanode
    const datanodeForm = new FormData()
    datanodeForm.append("file", file)
    datanodeForm.append("metadata", JSON.stringify(metadata))

    const datanodeResp = await fetch(`${uploadTo}/api/upload`, {
      method: "POST",
      body: datanodeForm,
    })

    if (!datanodeResp.ok) {
      const text = await datanodeResp.text().catch(() => "")
      return NextResponse.json(
        { success: false, message: `Datanode upload failed: ${text}` },
        { status: 502 }
      )
    }

    // Step 4️⃣ — Save metadata locally (for download)
    let metaStore: Record<string, any> = {}
    if (fs.existsSync(META_FILE)) {
      try {
        metaStore = JSON.parse(fs.readFileSync(META_FILE, "utf8"))
      } catch {
        metaStore = {}
      }
    }

    metaStore[filename] = { uploadTo, filename, blocks }
    fs.writeFileSync(META_FILE, JSON.stringify(metaStore, null, 2))

    // Step 5️⃣ — Return response
    const downloadUrl = `/api/download?file=${encodeURIComponent(filename)}`
    return NextResponse.json({
      success: true,
      message: "File uploaded successfully via Namenode → Datanode pipeline",
      namenode_response: namenodeData,
      datanode_node: uploadTo,
      download_api: downloadUrl,
    })
  } catch (error) {
    console.error("Upload pipeline error:", error)
    return NextResponse.json(
      { success: false, message: "Upload failed", error: String(error) },
      { status: 500 }
    )
  }
}
