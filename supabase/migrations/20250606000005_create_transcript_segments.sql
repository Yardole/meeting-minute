CREATE TABLE public.transcript_segments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  meeting_id UUID NOT NULL REFERENCES public.meetings(id) ON DELETE CASCADE,
  speaker_id UUID REFERENCES public.speakers(id) ON DELETE SET NULL,
  text TEXT NOT NULL,
  start_time_ms INTEGER NOT NULL,
  end_time_ms INTEGER NOT NULL,
  confidence REAL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_transcript_segments_meeting_id ON public.transcript_segments(meeting_id);

CREATE TRIGGER set_transcript_segments_updated_at
BEFORE UPDATE ON public.transcript_segments
FOR EACH ROW
EXECUTE FUNCTION public.set_updated_at();
