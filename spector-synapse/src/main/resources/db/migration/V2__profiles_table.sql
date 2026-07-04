-- Spector Synapse — V2: Profiles table for ChatMemoryRepository
-- Unified storage for agent souls and user profiles via SpectorMemory-backed repository

-- ── Profiles (Agent Soul + User Profile) ──
CREATE TABLE IF NOT EXISTS profiles (
    profile_type VARCHAR(50)  NOT NULL,  -- 'agent_soul' or 'user_profile'
    profile_id   VARCHAR(100) NOT NULL,  -- unique identifier within type
    content      CLOB         NOT NULL,  -- serialized profile content (JSON)
    metadata     CLOB,                   -- additional metadata (JSON)
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_type, profile_id)
);

CREATE INDEX idx_profiles_type ON profiles(profile_type, updated_at DESC);
