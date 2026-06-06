CREATE TABLE public.summaries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  meeting_id UUID NOT NULL UNIQUE REFERENCES public.meetings(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  key_points JSONB DEFAULT '[]',
  generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_summaries_meeting_id ON public.summaries(meeting_id);

CREATE TRIGGER set_summaries_updated_at
BEFORE UPDATE ON public.summaries
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();
