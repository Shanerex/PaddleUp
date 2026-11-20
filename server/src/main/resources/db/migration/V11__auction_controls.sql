-- Auction controls: the phases a Commissioner can put a live auction in. The event log is the
-- source of truth; this header column only drives boot discovery, so it must accept every phase.
ALTER TABLE auctions DROP CONSTRAINT auctions_phase_valid;
ALTER TABLE auctions ADD CONSTRAINT auctions_phase_valid CHECK (phase IN
    ('scheduled', 'live', 'paused', 'adjourned', 'resume_lobby', 'main_done', 'nominating',
     'ended'));
