-- Données de démonstration (SCRUM-68).
-- Chargées UNIQUEMENT avec le profil Spring "demo" (voir application-demo.properties).
-- Jamais en production. Rejouable : ON CONFLICT DO NOTHING partout.

-- 3 terrains à Marrakech
INSERT INTO venue (id, owner_id, name, latitude, longitude, price_per_hour_cents, currency, accepts_on_site_payment) VALUES
  ('a1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000001', 'Terrain Démo Guéliz',     31.6340, -8.0100, 15000, 'MAD', TRUE),
  ('a1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001', 'Terrain Démo Daoudiate',  31.6475, -7.9930, 12000, 'MAD', FALSE),
  ('a1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000002', 'Terrain Démo Massira',    31.6080, -8.0560, 10000, 'MAD', TRUE)
ON CONFLICT DO NOTHING;

-- Équipements (prix dans la devise du terrain)
INSERT INTO equipment (id, venue_id, name, price_cents) VALUES
  ('c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'Chasubles', 2000),
  ('c1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 'Ballon',    1000),
  ('c1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002', 'Chasubles', 2000),
  ('c1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002', 'Ballon',    1000),
  ('c1000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000003', 'Chasubles', 1500),
  ('c1000000-0000-4000-8000-000000000006', 'a1000000-0000-4000-8000-000000000003', 'Ballon',    1000)
ON CONFLICT DO NOTHING;

-- Créneaux d'1 h, de 18 h à 23 h (heure de Marrakech), pour les 14 prochains jours.
-- ON CONFLICT sans cible : ignore aussi bien UNIQUE (venue_id, start_at) que la contrainte EXCLUDE.
INSERT INTO slot (id, venue_id, start_at, end_at)
SELECT gen_random_uuid(),
       v.id,
       ((CURRENT_DATE + d) + make_interval(hours => h))     AT TIME ZONE 'Africa/Casablanca',
       ((CURRENT_DATE + d) + make_interval(hours => h + 1)) AT TIME ZONE 'Africa/Casablanca'
FROM venue v
CROSS JOIN generate_series(0, 13) AS d
CROSS JOIN generate_series(18, 22) AS h
WHERE v.id IN ('a1000000-0000-4000-8000-000000000001',
               'a1000000-0000-4000-8000-000000000002',
               'a1000000-0000-4000-8000-000000000003')
ON CONFLICT DO NOTHING;
