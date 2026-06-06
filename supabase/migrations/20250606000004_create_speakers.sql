CREATE TABLE public.speakers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  meeting_id UUID NOT NULL REFERENCES public.meetings(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  name TEXT,
  display_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_speakers_meeting_id ON public.speakers(meeting_id);

CREATE TRIGGER set_speakers_updated_at
BEFORE UPDATE ON public.speakers
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();
