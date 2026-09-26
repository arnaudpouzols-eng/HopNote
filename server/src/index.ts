export interface Env {
  DB: D1Database
  NOTION_CLIENT_ID: string
  NOTION_CLIENT_SECRET: string
  PUBLIC_BASE_URL: string
  TOKEN_ENCRYPTION_KEY: string
}

type NotionTokenResponse = { access_token: string }

const encoder = new TextEncoder()

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } })

const random = () => crypto.randomUUID().replaceAll("-", "") + crypto.randomUUID().replaceAll("-", "")

async function sha256(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value))
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("")
}

function base64Url(bytes: Uint8Array) {
  let binary = ""
  bytes.forEach(byte => binary += String.fromCharCode(byte))
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "")
}

function fromBase64Url(value: string) {
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/") + "===".slice((value.length + 3) % 4)
  return Uint8Array.from(atob(base64), char => char.charCodeAt(0))
}

async function encryptionKey(env: Env) {
  return crypto.subtle.importKey("raw", fromBase64Url(env.TOKEN_ENCRYPTION_KEY), "AES-GCM", false, ["encrypt", "decrypt"])
}

async function encrypt(env: Env, plaintext: string) {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await encryptionKey(env), encoder.encode(plaintext)))
  return `${base64Url(iv)}.${base64Url(ciphertext)}`
}

async function decrypt(env: Env, payload: string) {
  const [iv, ciphertext] = payload.split(".")
  if (!iv || !ciphertext) throw new Error("Invalid encrypted token")
  const plaintext = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64Url(iv) }, await encryptionKey(env), fromBase64Url(ciphertext))
  return new TextDecoder().decode(plaintext)
}

async function deviceId(request: Request, env: Env) {
  const token = request.headers.get("Authorization")?.replace(/^Bearer\s+/i, "")
  if (!token) return null
  const tokenHash = await sha256(token)
  return (await env.DB.prepare("SELECT id FROM device_sessions WHERE token_hash = ?").bind(tokenHash).first<{ id: string }>())?.id ?? null
}

async function notionRequest(accessToken: string, path: string, init: RequestInit = {}) {
  return fetch(`https://api.notion.com${path}`, {
    ...init,
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Notion-Version": "2026-03-11",
      "Content-Type": "application/json",
      ...(init.headers ?? {})
    }
  })
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    if (request.method === "GET" && url.pathname === "/health") return json({ ok: true })

    if (request.method === "POST" && url.pathname === "/v1/devices") {
      const id = crypto.randomUUID()
      const token = random()
      await env.DB.prepare("INSERT INTO device_sessions (id, token_hash, created_at) VALUES (?, ?, ?)")
        .bind(id, await sha256(token), Date.now()).run()
      return json({ deviceId: id, sessionToken: token }, 201)
    }

    if (request.method === "GET" && url.pathname === "/v1/notion/oauth/callback") {
      const code = url.searchParams.get("code")
      const state = url.searchParams.get("state")
      if (!code || !state) return new Response("Connexion Notion invalide.", { status: 400 })
      const pending = await env.DB.prepare("SELECT device_id, expires_at FROM oauth_states WHERE state = ?").bind(state).first<{ device_id: string, expires_at: number }>()
      await env.DB.prepare("DELETE FROM oauth_states WHERE state = ?").bind(state).run()
      if (!pending || pending.expires_at < Date.now()) return new Response("Cette connexion a expiré. Retourne dans HopNote.", { status: 400 })

      const credentials = btoa(`${env.NOTION_CLIENT_ID}:${env.NOTION_CLIENT_SECRET}`)
      const response = await fetch("https://api.notion.com/v1/oauth/token", {
        method: "POST",
        headers: { "Authorization": `Basic ${credentials}`, "Content-Type": "application/json" },
        body: JSON.stringify({ grant_type: "authorization_code", code, redirect_uri: `${env.PUBLIC_BASE_URL}/v1/notion/oauth/callback` })
      })
      if (!response.ok) return new Response("Notion a refusé la connexion. Retourne dans HopNote et réessaie.", { status: 502 })
      const token = (await response.json() as NotionTokenResponse).access_token
      await env.DB.prepare(`INSERT INTO notion_connections (device_id, encrypted_access_token, created_at, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(device_id) DO UPDATE SET encrypted_access_token = excluded.encrypted_access_token, updated_at = excluded.updated_at`)
        .bind(pending.device_id, await encrypt(env, token), Date.now(), Date.now()).run()
      return new Response("Notion est connecté. Tu peux revenir dans HopNote.", { headers: { "Content-Type": "text/plain; charset=UTF-8" } })
    }

    const id = await deviceId(request, env)
    if (!id) return json({ error: "Unauthorized" }, 401)

    if (request.method === "GET" && url.pathname === "/v1/notion/oauth/start") {
      const state = random()
      await env.DB.prepare("INSERT INTO oauth_states (state, device_id, expires_at) VALUES (?, ?, ?)")
        .bind(state, id, Date.now() + 10 * 60 * 1000).run()
      const authorize = new URL("https://api.notion.com/v1/oauth/authorize")
      authorize.search = new URLSearchParams({
        client_id: env.NOTION_CLIENT_ID,
        response_type: "code",
        owner: "user",
        redirect_uri: `${env.PUBLIC_BASE_URL}/v1/notion/oauth/callback`,
        state
      }).toString()
      return json({ authorizationUrl: authorize.toString() })
    }

    if (request.method === "GET" && url.pathname === "/v1/notion/status") {
      const connection = await env.DB.prepare("SELECT hopnote_page_id FROM notion_connections WHERE device_id = ?").bind(id).first<{ hopnote_page_id: string | null }>()
      return json({ connected: Boolean(connection), hopNotePageId: connection?.hopnote_page_id ?? null })
    }

    if (request.method === "POST" && url.pathname === "/v1/captures") {
      const body = await request.json<{ text?: string, source?: string, createdAt?: number }>()
      const text = body.text?.trim()
      if (!text || text.length > 5000) return json({ error: "Invalid capture" }, 400)
      const connection = await env.DB.prepare("SELECT encrypted_access_token, hopnote_page_id FROM notion_connections WHERE device_id = ?").bind(id).first<{ encrypted_access_token: string, hopnote_page_id: string | null }>()
      if (!connection?.hopnote_page_id) return json({ error: "Notion is not ready" }, 409)
      const response = await notionRequest(await decrypt(env, connection.encrypted_access_token), `/v1/blocks/${connection.hopnote_page_id}/children`, {
        method: "PATCH",
        body: JSON.stringify({ children: [{ object: "block", type: "paragraph", paragraph: { rich_text: [{ type: "text", text: { content: text } }] } }] })
      })
      if (!response.ok) return json({ error: "Notion sync failed" }, 502)
      const result = await response.json<{ results?: Array<{ id: string }> }>()
      return json({ notionBlockId: result.results?.[0]?.id ?? null }, 201)
    }

    return json({ error: "Not found" }, 404)
  }
} satisfies ExportedHandler<Env>
