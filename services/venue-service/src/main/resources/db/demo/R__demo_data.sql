-- SCRUM-68 : données de démo, chargées SEULEMENT avec le profil Spring « demo »
-- (local et staging, jamais en production). PostgreSQL uniquement.
-- Migration « repeatable » (R__) : rejouée si ce fichier change ; idempotente grâce à ON CONFLICT.

-- 3 terrains fictifs à Marrakech. Le gérant (owner_id) est un compte de démo fictif.
INSERT INTO venue (id, owner_id, name, latitude, longitude, price_per_hour_cents, currency,
                   accepts_on_site_payment, free_cancellation_hours, created_at)
VALUES
  ('a1000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000001',
   'Terrain Démo Guéliz', 31.6340, -8.0100, 15000, 'MAD', TRUE, 24, now()),
  ('a1000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001',
   'Terrain Démo Daoudiate', 31.6475, -7.9930, 12000, 'MAD', FALSE, 12, now()),
  ('a1000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000002',
   'Terrain Démo Massira', 31.6080, -8.0560, 10000, 'MAD', TRUE, 24, now())
ON CONFLICT (id) DO NOTHING;

-- Équipements : chasubles et ballon sur chaque terrain
INSERT INTO equipment (id, venue_id, name, extra_price_cents, currency)
VALUES
  ('c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'Chasubles', 2000, 'MAD'),
  ('c1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 'Ballon', 1000, 'MAD'),
  ('c1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000002', 'Chasubles', 2000, 'MAD'),
  ('c1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000002', 'Ballon', 1000, 'MAD'),
  ('c1000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000003', 'Chasubles', 1500, 'MAD'),
  ('c1000000-0000-4000-8000-000000000006', 'a1000000-0000-4000-8000-000000000003', 'Ballon', 1000, 'MAD')
ON CONFLICT (id) DO NOTHING;

-- Créneaux d'1 h, de 18 h à 23 h (heure de Marrakech), pour chaque terrain,
-- sur les 14 jours qui suivent la date où la migration est jouée.
-- Calculés à partir de la date du jour : ils ne sont pas figés sur une semaine précise.
INSERT INTO slot (id, venue_id, start_at, end_at)
SELECT gen_random_uuid(),
       v.id,
       ((CURRENT_DATE + d) + make_interval(hours => h)) AT TIME ZONE 'Africa/Casablanca',
       ((CURRENT_DATE + d) + make_interval(hours => h + 1)) AT TIME ZONE 'Africa/Casablanca'
FROM venue v
CROSS JOIN generate_series(0, 13) AS d
CROSS JOIN generate_series(18, 22) AS h
WHERE v.id IN ('a1000000-0000-4000-8000-000000000001',
               'a1000000-0000-4000-8000-000000000002',
               'a1000000-0000-4000-8000-000000000003')
ON CONFLICT (venue_id, start_at) DO NOTHING;
