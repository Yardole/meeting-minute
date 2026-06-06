import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.0'

interface SyncRow {
  id: string
  meeting_id?: string
  user_id?: string
  speaker_id?: string
  updated_at: string
  deleted_at: string | null
  [key: string]: unknown
}

interface SyncPayload {
  userId: string
  tables: {
    meetings?: SyncRow[]
    speakers?: SyncRow[]
    transcript_segments?: SyncRow[]
    summaries?: SyncRow[]
    chat_messages?: SyncRow[]
    profiles?: SyncRow[]
  }
}

const TABLE_ORDER = ['meetings', 'speakers', 'transcript_segments', 'summaries', 'chat_messages', 'profiles']

function toSnakeCase(str: string): string {
  return str.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

function convertRowToSnake(row: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(row)) {
    result[toSnakeCase(key)] = value
  }
  return result
}

function convertRowToCamel(row: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(row)) {
    const camelKey = key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
    result[camelKey] = value
  }
  return result
}

async function syncTable(
  supabase: ReturnType<typeof createClient>,
  tableName: string,
  localRows: SyncRow[],
  userId: string
): Promise<SyncRow[]> {
  // Query remote rows for this user's data
  const { data: remoteRows, error } = await supabase
    .from(tableName)
    .select('*')
    .order('updated_at', { ascending: false })

  if (error) {
    console.error(`Error fetching ${tableName}:`, error)
    // Return local data as-is if we can't fetch remote
    return localRows
  }

  const remoteMap = new Map<string, SyncRow>()
  for (const row of (remoteRows || [])) {
    remoteMap.set(row.id, row as SyncRow)
  }

  const localMap = new Map<string, SyncRow>()
  for (const row of localRows) {
    localMap.set(row.id, row)
  }

  const mergedMap = new Map<string, SyncRow>()

  // Process all unique IDs
  const allIds = new Set([...localMap.keys(), ...remoteMap.keys()])

  for (const id of allIds) {
    const local = localMap.get(id)
    const remote = remoteMap.get(id)

    if (local && !remote) {
      // Only local exists → push to remote
      const snakeRow = convertRowToSnake(local as unknown as Record<string, unknown>)
      const { error: upsertErr } = await supabase
        .from(tableName)
        .upsert(snakeRow, { onConflict: 'id' })
      if (upsertErr) {
        console.error(`Failed to push ${tableName}/${id}:`, upsertErr)
      }
      mergedMap.set(id, local)
    } else if (!local && remote) {
      // Only remote exists → pull to local
      const camelRow = convertRowToCamel(remote as unknown as Record<string, unknown>)
      mergedMap.set(id, camelRow as unknown as SyncRow)
    } else if (local && remote) {
      // Both exist → compare updated_at
      const localTime = new Date(local.updated_at).getTime()
      const remoteTime = new Date(remote.updated_at).getTime()

      if (local.deleted_at && !remote.deleted_at) {
        // Local deleted → propagate to remote
        const { error: delErr } = await supabase
          .from(tableName)
          .update({ deleted_at: local.deleted_at, updated_at: new Date().toISOString() })
          .eq('id', id)
        if (delErr) console.error(`Failed to propagate delete ${tableName}/${id}:`, delErr)
        mergedMap.set(id, local)
      } else if (remote.deleted_at && !local.deleted_at) {
        // Remote deleted → pull
        const camelRow = convertRowToCamel(remote as unknown as Record<string, unknown>)
        mergedMap.set(id, camelRow as unknown as SyncRow)
      } else if (localTime > remoteTime) {
        // Local newer → push to remote
        const snakeRow = convertRowToSnake(local as unknown as Record<string, unknown>)
        const { error: upsertErr } = await supabase
          .from(tableName)
          .upsert(snakeRow, { onConflict: 'id' })
        if (upsertErr) {
          console.error(`Failed to push newer ${tableName}/${id}:`, upsertErr)
        }
        mergedMap.set(id, local)
      } else if (remoteTime > localTime) {
        // Remote newer → pull
        const camelRow = convertRowToCamel(remote as unknown as Record<string, unknown>)
        mergedMap.set(id, camelRow as unknown as SyncRow)
      } else {
        // Equal → skip (local wins tie)
        mergedMap.set(id, local)
      }
    }
  }

  return Array.from(mergedMap.values())
}

serve(async (req) => {
  try {
    const payload: SyncPayload = await req.json()
    const { userId } = payload

    if (!userId) {
      return new Response(
        JSON.stringify({ error: 'userId is required' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    )

    const result: Record<string, SyncRow[]> = {}

    for (const tableName of TABLE_ORDER) {
      const localRows = payload.tables?.[tableName as keyof typeof payload.tables] || []
      result[tableName] = await syncTable(supabase, tableName, localRows, userId)
    }

    return new Response(
      JSON.stringify({ status: 'synced', tables: result }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (e) {
    console.error('Sync error:', e)
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
