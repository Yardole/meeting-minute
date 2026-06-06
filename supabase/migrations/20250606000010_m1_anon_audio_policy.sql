-- Temporary: allow anon uploads and reads for M1 development
-- Remove once auth is fully wired in the Android app

-- Make the audio bucket public so uploads work via anon key
UPDATE storage.buckets SET public = true WHERE id = 'audio';

CREATE POLICY "Anon can upload to audio bucket"
  ON storage.objects FOR INSERT
  WITH CHECK (
    bucket_id = 'audio'
  );

CREATE POLICY "Anon can read from audio bucket"
  ON storage.objects FOR SELECT
  USING (
    bucket_id = 'audio'
  );
