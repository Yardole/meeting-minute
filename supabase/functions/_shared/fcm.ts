interface FcmPayload {
  title: string
  body: string
  meetingId: string
}

let cachedToken: { token: string; expiresAt: number } | null = null

async function getAccessToken(): Promise<string | null> {
  const serviceAccountJson = Deno.env.get('FCM_SERVICE_ACCOUNT_JSON')
  if (!serviceAccountJson) {
    console.log('FCM_SERVICE_ACCOUNT_JSON not set, skipping push notification')
    return null
  }

  // Check cached token
  if (cachedToken && Date.now() < cachedToken.expiresAt) {
    return cachedToken.token
  }

  try {
    const sa = JSON.parse(serviceAccountJson)
    const now = Math.floor(Date.now() / 1000)
    const jwt = await createJwt(sa, now)

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion: jwt,
      }),
    })

    if (!res.ok) {
      console.error('OAuth2 token fetch failed:', await res.text())
      return null
    }

    const data = await res.json()
    cachedToken = {
      token: data.access_token,
      expiresAt: Date.now() + (data.expires_in - 60) * 1000, // buffer 60s
    }
    return cachedToken.token
  } catch (e) {
    console.error('Failed to get FCM access token:', e)
    return null
  }
}

async function createJwt(sa: any, now: number): Promise<string> {
  // Minimal JWT creation for Google OAuth2 (no external library needed)
  const header = { alg: 'RS256', typ: 'JWT' }
  const claim = {
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: sa.token_uri || 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  }

  const encoder = new TextEncoder()
  const headerB64 = btoa(JSON.stringify(header)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  const claimB64 = btoa(JSON.stringify(claim)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
  const toSign = `${headerB64}.${claimB64}`

  const keyData = encoder.encode(toSign)
  const key = await importPrivateKey(sa.private_key)
  const signature = await crypto.subtle.sign({ name: 'RSASSA-PKCS1-v1_5' }, key, keyData)
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')

  return `${toSign}.${sigB64}`
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemContents = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '')
  const binaryDer = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0))
  return crypto.subtle.importKey(
    'pkcs8',
    binaryDer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  )
}

export async function sendPush(
  supabase: any,
  userId: string,
  payload: FcmPayload
): Promise<void> {
  const projectId = 'oliva-meeting-minutes'
  const accessToken = await getAccessToken()
  if (!accessToken) return

  // Get the user's FCM token from their profile
  const { data: profile, error } = await supabase
    .from('profiles')
    .select('fcm_token')
    .eq('id', userId)
    .single()

  if (error || !profile?.fcm_token) {
    console.log(`No FCM token for user ${userId}, skipping push`)
    return
  }

  const fcmPayload = {
    message: {
      token: profile.fcm_token,
      data: {
        title: payload.title,
        body: payload.body,
        meeting_id: payload.meetingId,
      },
      notification: {
        title: payload.title,
        body: payload.body,
      },
    },
  }

  try {
    const res = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(fcmPayload),
      }
    )

    if (!res.ok) {
      const err = await res.text()
      console.error('FCM send failed:', err)
    } else {
      console.log(`Push sent to user ${userId} for meeting ${payload.meetingId}`)
    }
  } catch (e) {
    console.error('FCM send error:', e)
  }
}
