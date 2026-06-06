CREATE TYPE public.meeting_status AS ENUM (
  'recording',
  'recorded',
  'transcribing',
  'transcribed',
  'summarizing',
  'summarized',
  'error'
);

CREATE TABLE public.meetings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL DEFAULT '',
  duration_ms INTEGER DEFAULT 0,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  audio_url TEXT,
  audio_bucket_path TEXT,
  status public.meeting_status NOT NULL DEFAULT 'recording',
  language TEXT DEFAULT 'en',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_meetings_user_id ON public.meetings(user_id);
CREATE INDEX idx_meetings_deleted_at ON public.meetings(deleted_at);

CREATE TRIGGER set_meetings_updated_at
BEFORE UPDATE ON public.meetings
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();
