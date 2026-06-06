import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.0'

const DEEPSEEK_URL = 'https://api.deepseek.com/v1/chat/completions'

serve(async (req) => {
  try {
    const { transcript, newMessage, meetingId } = await req.json()
    const deepseekApiKey = Deno.env.get('DEEPSEEK_API_KEY')

    if (!deepseekApiKey) {
      return new Response(
        JSON.stringify({ error: 'DEEPSEEK_API_KEY not set' }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    if (!transcript || !newMessage || !meetingId) {
      return new Response(
        JSON.stringify({ error: 'transcript, newMessage, and meetingId are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    )

    // Fetch existing chat history from Supabase
    const { data: chatHistory } = await supabase
      .from('chat_messages')
      .select('role, content')
      .eq('meeting_id', meetingId)
      .is('deleted_at', null)
      .order('created_at', { ascending: true })
      .limit(50)

    const systemPrompt = `You are a helpful AI assistant. You are answering questions about a meeting based on its transcript.
Use the provided transcript as your primary context. Answer questions accurately based on what was discussed.
If the answer cannot be found in the transcript, say so honestly. Keep responses concise and relevant.

Meeting Transcript:
${transcript}`

    const messages: Array<{ role: string; content: string }> = [
      { role: 'system', content: systemPrompt },
    ]

    // Add chat history
    if (chatHistory) {
      for (const msg of chatHistory) {
        messages.push({ role: msg.role, content: msg.content })
      }
    }

    // Add the new user message
    messages.push({ role: 'user', content: newMessage })

    const response = await fetch(DEEPSEEK_URL, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${deepseekApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'deepseek-chat',
        messages,
        temperature: 0.7,
        max_tokens: 2048,
      }),
    })

    if (!response.ok) {
      const err = await response.text()
      return new Response(
        JSON.stringify({ error: `DeepSeek API error: ${err}` }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }

    const data = await response.json()
    const reply = data.choices?.[0]?.message?.content || 'Sorry, I could not generate a response.'

    // Insert assistant message into Supabase
    const assistantMsgId = crypto.randomUUID()
    const { error: insertError } = await supabase.from('chat_messages').insert({
      id: assistantMsgId,
      meeting_id: meetingId,
      role: 'assistant',
      content: reply,
    })

    if (insertError) {
      console.error('Insert chat message error:', insertError)
    }

    return new Response(
      JSON.stringify({
        status: 'completed',
        reply,
        messageId: assistantMsgId,
      }),
      { headers: { 'Content-Type': 'application/json' } }
    )
  } catch (e) {
    console.error('Chat error:', e)
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
