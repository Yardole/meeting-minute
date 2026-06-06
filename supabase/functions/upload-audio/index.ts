import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.0'
import { decode } from 'https://deno.land/std@0.177.0/encoding/base64.ts'

serve(async (req) => {
  try {
    const { meetingId, audioBase64 } = await req.json()

    if (!meetingId || !audioBase64) {
      return new Response(
        JSON.stringify({ error: 'meetingId and audioBase64 are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    )

    const audioBytes = decode(audioBase64)
    const bucketPath = `recordings/${meetingId}.m4a`

    const { data, error } = await supabase.storage
      .from('audio')
      .upload(bucketPath, audioBytes, {
        contentType: 'audio/mp4',
        upsert: true,
      })

    if (error) {
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const { data: urlData } = supabase.storage
      .from('audio')
      .getPublicUrl(bucketPath)

    return new Response(
      JSON.stringify({ audioUrl: urlData.publicUrl }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (e) {
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
