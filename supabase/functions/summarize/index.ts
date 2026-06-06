import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'

serve(async (req) => {
  const { transcript, meetingId } = await req.json()
  const deepseekApiKey = Deno.env.get('DEEPSEEK_API_KEY')

  if (!deepseekApiKey) {
    return new Response(
      JSON.stringify({ error: 'DEEPSEEK_API_KEY not set' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }

  // TODO: Call DeepSeek API for summary + key points
  // TODO: Insert summary
  // TODO: Update meeting.status to 'summarized'

  return new Response(
    JSON.stringify({ status: 'stub', transcriptLength: transcript?.length, meetingId }),
    { headers: { 'Content-Type': 'application/json' } }
  )
})
