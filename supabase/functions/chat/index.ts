import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'

serve(async (req) => {
  const { transcript, messages, newMessage, meetingId } = await req.json()
  const deepseekApiKey = Deno.env.get('DEEPSEEK_API_KEY')

  if (!deepseekApiKey) {
    return new Response(
      JSON.stringify({ error: 'DEEPSEEK_API_KEY not set' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }

  // TODO: Call DeepSeek API with transcript context + chat history
  // TODO: Insert assistant chat_message

  return new Response(
    JSON.stringify({ status: 'stub', reply: 'Hello from chat stub', meetingId }),
    { headers: { 'Content-Type': 'application/json' } }
  )
})
