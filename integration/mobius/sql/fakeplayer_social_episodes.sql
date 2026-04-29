DROP TABLE IF EXISTS `fakeplayer_social_episodes`;
CREATE TABLE IF NOT EXISTS `fakeplayer_social_episodes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `speaker_key` varchar(64) NOT NULL,
  `player_key` varchar(64) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `summary` text DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE INDEX idx_fakeplayer_social_pair ON fakeplayer_social_episodes (speaker_key, player_key, created_at);
