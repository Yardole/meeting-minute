import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.0'

const ASSEMBLY_AI_URL = 'https://api.assemblyai.com/v2'

async function pollTranscript(transcriptId: string, apiKey: string): Promise<any> {
  const maxAttempts = 120
  for (let i = 0; i < maxAttempts; i++) {
    const res = await fetch(`${ASSEMBLY_AI_URL}/transcript/${transcriptId}`, {
      headers: { 'Authorization': apiKey }
    })
    const data = await res.json()
    if (data.status === 'completed') return data
    if (data.status === 'error') throw new Error(`AssemblyAI error: ${data.error}`)
    await new Promise(r => setTimeout(r, 1000))
  }
  throw new Error('Transcription timed out')
}

serve(async (req) => {
  try {
    const { audioUrl, meetingId } = await req.json()
    const assemblyApiKey = Deno.env.get('ASSEMBLY_AI_API_KEY')

    if (!assemblyApiKey) {
      return new Response(
        JSON.stringify({ error: 'ASSEMBLY_AI_API_KEY not set' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    if (!audioUrl || !meetingId) {
      return new Response(
        JSON.stringify({ error: 'audioUrl and meetingId are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    )

    await supabase.from('meetings').update({ status: 'TRANSCRIBING' }).eq('id', meetingId)

    const submitRes = await fetch(`${ASSEMBLY_AI_URL}/transcript`, {
      method: 'POST',
      headers: {
        'Authorization': assemblyApiKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        audio_url: audioUrl,
        speaker_labels: true,
      }),
    })

    if (!submitRes.ok) {
      const err = await submitRes.json()
      await supabase.from('meetings').update({ status: 'ERROR' }).eq('id', meetingId)
      return new Response(
        JSON.stringify({ error: `AssemblyAI submit failed: ${JSON.stringify(err)}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const { id: transcriptId } = await submitRes.json()
    const result = await pollTranscript(transcriptId, assemblyApiKey)
    const utterances: any[] = result.utterances || []
    const fullText: string = result.text || ''

    let speakers: Array<{ id: string; label: string; name: string; order: number }> = []
    let segments: Array<{
      id: string; speakerId: string | null; speakerName: string;
      text: string; startTimeMs: number; endTimeMs: number; confidence: number | null
    }> = []

    if (utterances.length > 0) {
      // Use speaker-diarized utterances
      const uniqueSpeakers = new Map<string, { id: string; label: string; name: string; order: number }>()
      utterances.forEach(u => {
        const label = `Speaker ${u.speaker}`
        if (!uniqueSpeakers.has(label)) {
          uniqueSpeakers.set(label, {
            id: crypto.randomUUID(),
            label,
            name: label,
            order: uniqueSpeakers.size,
          })
        }
      })

      speakers = Array.from(uniqueSpeakers.values())
      const speakerMap = new Map(speakers.map(s => [s.label, s.id]))

      segments = utterances.map(u => ({
        id: crypto.randomUUID(),
        speakerId: speakerMap.get(`Speaker ${u.speaker}`) || null,
        speakerName: `Speaker ${u.speaker}`,
        text: u.text,
        startTimeMs: u.start,
        endTimeMs: u.end,
        confidence: u.confidence || null,
      }))
    } else if (fullText) {
      // Fallback: no diarization available, use full transcript as single segment
      const speakerId = crypto.randomUUID()
      speakers = [{
        id: speakerId,
        label: 'Speaker 1',
        name: 'Speaker 1',
        order: 0,
      }]
      // Estimate timing from words if available, otherwise use full duration
      const words: any[] = result.words || []
      if (words.length > 0) {
        const firstWord = words[0]
        const lastWord = words[words.length - 1]
        segments = [{
          id: crypto.randomUUID(),
          speakerId,
          speakerName: 'Speaker 1',
          text: fullText,
          startTimeMs: firstWord.start || 0,
          endTimeMs: lastWord.end || (result.audio_duration || 0) * 1000,
          confidence: result.confidence || null,
        }]
      } else {
        segments = [{
          id: crypto.randomUUID(),
          speakerId,
          speakerName: 'Speaker 1',
          text: fullText,
          startTimeMs: 0,
          endTimeMs: (result.audio_duration || 0) * 1000,
          confidence: result.confidence || null,
        }]
      }
    }

    await supabase.from('meetings').update({ status: 'TRANSCRIBED' }).eq('id', meetingId)

    return new Response(
      JSON.stringify({ status: 'completed', speakers, segments }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (e) {
    console.error('Transcribe error:', e)
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
