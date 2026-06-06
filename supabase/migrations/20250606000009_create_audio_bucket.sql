-- Create the audio bucket for meeting recordings
INSERT INTO storage.buckets (id, name, public)
VALUES ('audio', 'audio', false)
ON CONFLICT (id) DO NOTHING;

-- Allow users to upload their own audio files
CREATE POLICY "Users can upload own audio"
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'audio'
    AND owner = auth.uid()
  );

-- Allow users to read their own audio files
CREATE POLICY "Users can read own audio"
  ON storage.objects FOR SELECT
  USING (
    bucket_id = 'audio'
    AND owner = auth.uid()
  );

-- Allow users to delete their own audio files
CREATE POLICY "Users can delete own audio"
  ON storage.objects FOR DELETE
  USING (
    bucket_id = 'audio'
    AND owner = auth.uid()
  );
