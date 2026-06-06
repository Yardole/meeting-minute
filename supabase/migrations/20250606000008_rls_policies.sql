-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.meetings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.speakers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transcript_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;

-- Profiles: users can only read/update their own profile
CREATE POLICY "Users can view own profile"
  ON public.profiles FOR SELECT
  USING (id = auth.uid());

CREATE POLICY "Users can update own profile"
  ON public.profiles FOR UPDATE
  USING (id = auth.uid());

-- Meetings: users can only access their own meetings
CREATE POLICY "Users can view own meetings"
  ON public.meetings FOR SELECT
  USING (user_id = auth.uid() AND deleted_at IS NULL);

CREATE POLICY "Users can create meetings"
  ON public.meetings FOR INSERT
  WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can update own meetings"
  ON public.meetings FOR UPDATE
  USING (user_id = auth.uid());

CREATE POLICY "Users can delete own meetings"
  ON public.meetings FOR DELETE
  USING (user_id = auth.uid());

-- Speakers: via meeting ownership
CREATE POLICY "Users can view speakers for own meetings"
  ON public.speakers FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = speakers.meeting_id AND m.user_id = auth.uid()
  ) AND deleted_at IS NULL);

CREATE POLICY "Users can modify speakers for own meetings"
  ON public.speakers FOR ALL
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = speakers.meeting_id AND m.user_id = auth.uid()
  ));

-- Transcript segments: via meeting ownership
CREATE POLICY "Users can view segments for own meetings"
  ON public.transcript_segments FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = transcript_segments.meeting_id AND m.user_id = auth.uid()
  ) AND deleted_at IS NULL);

CREATE POLICY "Users can modify segments for own meetings"
  ON public.transcript_segments FOR ALL
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = transcript_segments.meeting_id AND m.user_id = auth.uid()
  ));

-- Summaries: via meeting ownership
CREATE POLICY "Users can view summaries for own meetings"
  ON public.summaries FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = summaries.meeting_id AND m.user_id = auth.uid()
  ) AND deleted_at IS NULL);

CREATE POLICY "Users can modify summaries for own meetings"
  ON public.summaries FOR ALL
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = summaries.meeting_id AND m.user_id = auth.uid()
  ));

-- Chat messages: via meeting ownership
CREATE POLICY "Users can view messages for own meetings"
  ON public.chat_messages FOR SELECT
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = chat_messages.meeting_id AND m.user_id = auth.uid()
  ) AND deleted_at IS NULL);

CREATE POLICY "Users can modify messages for own meetings"
  ON public.chat_messages FOR ALL
  USING (EXISTS (
    SELECT 1 FROM public.meetings m
    WHERE m.id = chat_messages.meeting_id AND m.user_id = auth.uid()
  ));

-- Auto-create profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, display_name)
  VALUES (NEW.id, NEW.email);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_user();
