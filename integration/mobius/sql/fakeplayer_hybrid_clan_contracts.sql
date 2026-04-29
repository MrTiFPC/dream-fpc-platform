DROP TABLE IF EXISTS `fakeplayer_hybrid_clan_contracts`;
CREATE TABLE IF NOT EXISTS `fakeplayer_hybrid_clan_contracts` (
  `fpc_id` varchar(64) NOT NULL,
  `fpc_name` varchar(64) NOT NULL,
  `clan_id` int(11) NOT NULL,
  `clan_name` varchar(64) NOT NULL,
  `leader_object_id` int(11) NOT NULL,
  `leader_name` varchar(64) NOT NULL,
  `pledge_type` int(11) NOT NULL,
  `power_grade` int(11) NOT NULL,
  `accepted_at` bigint(20) NOT NULL,
  PRIMARY KEY (`fpc_id`)
) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE INDEX idx_fakeplayer_hybrid_clan_clan ON fakeplayer_hybrid_clan_contracts (clan_id);
