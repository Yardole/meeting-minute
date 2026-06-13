import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.0'
import { sendPush } from '../_shared/fcm.ts'

const DEEPSEEK_URL = 'https://api.deepseek.com/v1/chat/completions'

serve(async (req) => {
  try {
    const { transcript, meetingId } = await req.json()
    const deepseekApiKey = Deno.env.get('DEEPSEEK_API_KEY')

    if (!deepseekApiKey) {
      return new Response(
        JSON.stringify({ error: 'DEEPSEEK_API_KEY not set' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    if (!transcript || !meetingId) {
      return new Response(
        JSON.stringify({ error: 'transcript and meetingId are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    )

    await supabase.from('meetings').update({ status: 'summarizing' }).eq('id', meetingId)

    const systemPrompt = `You are Oliva, an expert meeting assistant. Analyze the provided transcript and produce:

1. A short, descriptive meeting title (5-8 words max) that captures the main topic.
2. A concise, well-structured summary covering key decisions, action items, and important discussion points.
3. A list of key points (3-7 bullet points) highlighting the most important takeaways.
4. Identify speaker names from the conversation context. The transcript labels speakers generically (e.g. "Speaker A", "Speaker B"). Use context clues like introductions ("Hi, I'm John"), names used in conversation ("What do you think, Sarah?"), and self-references to figure out each speaker's actual name. If you cannot determine a name, use null for that speaker.

You must respond with a JSON object in this exact format:
{
  "title": "Short descriptive title here",
  "content": "Full meeting summary here...",
  "key_points": ["Key point 1", "Key point 2", "Key point 3"],
  "speakers": {
    "Speaker A": "John",
    "Speaker B": "Sarah"
  }
}`

    const response = await fetch(DEEPSEEK_URL, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${deepseekApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'deepseek-v4-flash',
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: `Please summarize this meeting transcript:\n\n${transcript}` }
        ],
        temperature: 0.3,
      }),
    })

    if (!response.ok) {
      const err = await response.text()
      await supabase.from('meetings').update({ status: 'error' }).eq('id', meetingId)
      return new Response(
        JSON.stringify({ error: `DeepSeek API error: ${err}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const data = await response.json()
    const content = data.choices?.[0]?.message?.content || ''

    // Parse the JSON response from DeepSeek
    let parsed: { title?: string; content: string; key_points: string[] }
    try {
      // Handle possible markdown code block wrapping
      const jsonStr = content.replace(/```json\n?/g, '').replace(/```\n?/g, '').trim()
      parsed = JSON.parse(jsonStr)
    } catch {
      // Fallback: treat entire response as summary content
      parsed = { content, key_points: [] }
    }

    const title = parsed.title || 'Untitled Meeting'

    // Update meeting title in Supabase
    await supabase.from('meetings').update({ title }).eq('id', meetingId)

    // Insert summary into Supabase
    const { error: insertError } = await supabase.from('summaries').upsert({
      meeting_id: meetingId,
      content: parsed.content || content,
      key_points: parsed.key_points || [],
      generated_at: new Date().toISOString(),
    }, { onConflict: 'meeting_id' })

    if (insertError) {
      console.error('Insert summary error:', insertError)
    }

    await supabase.from('meetings').update({ status: 'summarized', title }).eq('id', meetingId)

    // Auto-name speakers if AI identified them
    if (parsed.speakers) {
      const { data: existingSpeakers } = await supabase
        .from('speakers')
        .select('id, label, name')
        .eq('meeting_id', meetingId)
        .is('deleted_at', null)

      if (existingSpeakers) {
        for (const speaker of existingSpeakers) {
          const detectedName = parsed.speakers[speaker.label]
          if (detectedName && !speaker.name) {
            await supabase.from('speakers')
              .update({ name: detectedName })
              .eq('id', speaker.id)
          }
        }
      }
    }

    // Send push notification
    const { data: meeting } = await supabase.from('meetings').select('user_id').eq('id', meetingId).single()
    if (meeting?.user_id) {
      await sendPush(supabase, meeting.user_id, {
        title: 'Meeting ready',
        body: `"${title}" is ready to view.`,
        meetingId,
      })
    }

    return new Response(
      JSON.stringify({
        status: 'completed',
        title,
        content: parsed.content || content,
        keyPoints: parsed.key_points || [],
        speakers: parsed.speakers || {},
      }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (e) {
    console.error('Summarize error:', e)
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
