-- Add refresh_token column to social_links table
-- Required for Apple Sign In token revocation on account deletion (App Store requirement)

ALTER TABLE auth.social_links
ADD COLUMN refresh_token TEXT;

COMMENT ON COLUMN auth.social_links.refresh_token IS 'OAuth provider refresh token for revocation on account deletion';
