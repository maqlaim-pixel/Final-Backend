-- ============================================================
-- TravelVista — Complete PostgreSQL Schema
-- Generated from JPA/Hibernate entity models
-- Database: PostgreSQL 15+
-- ============================================================

-- Drop existing tables (reverse dependency order)
DROP TABLE IF EXISTS admin_audit_logs CASCADE;
DROP TABLE IF EXISTS edit_sessions CASCADE;
DROP TABLE IF EXISTS otp_verifications CASCADE;
DROP TABLE IF EXISTS menu_destinations CASCADE;
DROP TABLE IF EXISTS menus CASCADE;
DROP TABLE IF EXISTS faqs CASCADE;
DROP TABLE IF EXISTS site_settings CASCADE;
DROP TABLE IF EXISTS testimonials CASCADE;
DROP TABLE IF EXISTS wishlists CASCADE;
DROP TABLE IF EXISTS reviews CASCADE;
DROP TABLE IF EXISTS bookings CASCADE;
DROP TABLE IF EXISTS enquiries CASCADE;
DROP TABLE IF EXISTS leads CASCADE;
DROP TABLE IF EXISTS activities CASCADE;
DROP TABLE IF EXISTS blogs CASCADE;
DROP TABLE IF EXISTS hotels CASCADE;
DROP TABLE IF EXISTS packages CASCADE;
DROP TABLE IF EXISTS destinations CASCADE;
DROP TABLE IF EXISTS cities CASCADE;
DROP TABLE IF EXISTS states CASCADE;
DROP TABLE IF EXISTS countries CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;

-- ============================================================
-- 1. ROLES
-- ============================================================
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description TEXT
);

INSERT INTO roles (name, description) VALUES
    ('super_admin',   'Full access to all modules'),
    ('editor',        'Can create and publish content'),
    ('contributor',   'Can create draft content only'),
    ('sales',         'Leads and enquiries management'),
    ('customer',      'Regular website user');

-- ============================================================
-- 2. USERS
-- ============================================================
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150)  NOT NULL,
    email          VARCHAR(150)  NOT NULL UNIQUE,
    phone          VARCHAR(20),
    password_hash  VARCHAR(255)  NOT NULL,
    role_id        BIGINT        REFERENCES roles(id),
    profile_image  VARCHAR(500),
    is_active      BOOLEAN       DEFAULT TRUE,
    email_verified BOOLEAN       DEFAULT FALSE,
    phone_verified BOOLEAN       DEFAULT FALSE,
    created_at     TIMESTAMP     DEFAULT NOW(),
    updated_at     TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_users_email    ON users(email);
CREATE UNIQUE INDEX uq_users_email_normalized ON users(lower(btrim(email)));
CREATE UNIQUE INDEX uq_users_phone_normalized ON users(phone) WHERE phone IS NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_phone_normalized CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}
CREATE INDEX idx_users_role     ON users(role_id);

-- ============================================================
-- 3. COUNTRIES
-- ============================================================
CREATE TABLE countries (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    code            VARCHAR(10),                  -- ISO code: IN, US, TH
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    is_indian       BOOLEAN       DEFAULT TRUE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 4. STATES
-- ============================================================
CREATE TABLE states (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    country_id      BIGINT        NOT NULL REFERENCES countries(id),
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_states_country ON states(country_id);

-- ============================================================
-- 5. CITIES
-- ============================================================
CREATE TABLE cities (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    state_id        BIGINT        REFERENCES states(id),
    country_id      BIGINT        REFERENCES countries(id),
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_cities_state   ON cities(state_id);
CREATE INDEX idx_cities_country ON cities(country_id);

-- ============================================================
-- 6. DESTINATIONS
-- ============================================================
CREATE TABLE destinations (
    id                        BIGSERIAL PRIMARY KEY,
    name                      VARCHAR(200)  NOT NULL,
    slug                      VARCHAR(220)  NOT NULL UNIQUE,
    country                   VARCHAR(100),
    state                     VARCHAR(100),
    type                      VARCHAR(100)  DEFAULT 'domestic',
    description               TEXT,
    short_description         VARCHAR(500),
    image                     VARCHAR(500),
    tagline                   VARCHAR(300),
    best_time                 VARCHAR(100),
    avg_temp                  VARCHAR(100),
    languages                 VARCHAR(200),
    highlights                TEXT,
    package_count             INTEGER       DEFAULT 0,
    status                    VARCHAR(20)   DEFAULT 'draft',
    featured                  BOOLEAN       DEFAULT FALSE,
    sort_order                INTEGER       DEFAULT 0,

    -- CMS Hero section
    hero_images               TEXT,
    hero_title                VARCHAR(200),
    hero_subtitle             VARCHAR(500),
    hero_cta_text             VARCHAR(100),
    hero_cta_url              VARCHAR(500),

    -- CMS About section
    about_title               VARCHAR(200),
    about_content             TEXT,
    about_image               VARCHAR(500),
    about_image_position      VARCHAR(20)   DEFAULT 'right',

    -- CMS JSON sections
    attractions               TEXT,         -- JSON array
    experiences               TEXT,         -- JSON array
    destination_highlights    TEXT,         -- JSON array
    quick_info                TEXT,         -- JSON array

    -- SEO
    seo_title                 VARCHAR(200),
    seo_description           VARCHAR(500),
    og_title                  VARCHAR(200),
    og_description            VARCHAR(500),
    og_image                  VARCHAR(500),
    canonical_url             VARCHAR(500),
    no_index                  BOOLEAN       DEFAULT FALSE,

    -- Section visibility toggles
    show_attractions          BOOLEAN       DEFAULT TRUE,
    show_experiences          BOOLEAN       DEFAULT TRUE,
    show_highlights           BOOLEAN       DEFAULT TRUE,
    show_packages             BOOLEAN       DEFAULT TRUE,
    show_quick_info           BOOLEAN       DEFAULT TRUE,

    -- Coming soon
    packages_coming_soon      BOOLEAN       DEFAULT FALSE,
    packages_coming_soon_text VARCHAR(500),

    created_at                TIMESTAMP     DEFAULT NOW(),
    updated_at                TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_destinations_slug   ON destinations(slug);
CREATE INDEX idx_destinations_state  ON destinations(state);
CREATE INDEX idx_destinations_type   ON destinations(type);
CREATE INDEX idx_destinations_status ON destinations(status);

-- ============================================================
-- 7. PACKAGES (Travel Packages)
-- ============================================================
CREATE TABLE packages (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(200)    NOT NULL,
    slug               VARCHAR(220)    NOT NULL UNIQUE,
    description        TEXT,
    short_description  VARCHAR(500),
    destination        VARCHAR(200),
    state              VARCHAR(100),
    country            VARCHAR(100),
    duration_days      INTEGER         DEFAULT 0,
    duration_nights    INTEGER         DEFAULT 0,
    starting_price     NUMERIC(12,2),
    currency           VARCHAR(10)     DEFAULT 'INR',
    cover_image        VARCHAR(500),
    highlights         TEXT,
    inclusions         TEXT,
    exclusions         TEXT,
    itinerary          JSONB,          -- PostgreSQL JSONB
    category           VARCHAR(50)     DEFAULT 'domestic',
    tags               VARCHAR(255),
    status             VARCHAR(20)     DEFAULT 'draft',
    featured           BOOLEAN         DEFAULT FALSE,
    rating             NUMERIC(3,1)    DEFAULT 0,
    review_count       INTEGER         DEFAULT 0,
    created_at         TIMESTAMP       DEFAULT NOW(),
    updated_at         TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_packages_slug       ON packages(slug);
CREATE INDEX idx_packages_status     ON packages(status);
CREATE INDEX idx_packages_category   ON packages(category);
CREATE INDEX idx_packages_state      ON packages(state);
CREATE INDEX idx_packages_destination ON packages(destination);
CREATE INDEX idx_packages_featured   ON packages(featured) WHERE featured = TRUE;
CREATE INDEX idx_packages_tags       ON packages USING GIN (to_tsvector('english', COALESCE(tags, '')));

-- ============================================================
-- 8. HOTELS
-- ============================================================
CREATE TABLE hotels (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200)    NOT NULL,
    slug             VARCHAR(220)    NOT NULL UNIQUE,
    location         VARCHAR(200),
    city             VARCHAR(100),
    country          VARCHAR(100),
    description      TEXT,
    image            VARCHAR(500),
    category         VARCHAR(50),
    rating           NUMERIC(3,1)    DEFAULT 0,
    review_count     INTEGER         DEFAULT 0,
    price_per_night  NUMERIC(12,2),
    currency         VARCHAR(10)     DEFAULT 'INR',
    amenities        TEXT,            -- JSON array
    room_types       TEXT,            -- JSON array
    status           VARCHAR(20)     DEFAULT 'draft',
    featured         BOOLEAN         DEFAULT FALSE,
    created_at       TIMESTAMP       DEFAULT NOW(),
    updated_at       TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_hotels_slug   ON hotels(slug);
CREATE INDEX idx_hotels_status ON hotels(status);

-- ============================================================
-- 9. ACTIVITIES
-- ============================================================
CREATE TABLE activities (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200)    NOT NULL,
    slug          VARCHAR(220)    NOT NULL UNIQUE,
    location      VARCHAR(200),
    category      VARCHAR(100),
    description   TEXT,
    image         VARCHAR(500),
    duration      VARCHAR(50),
    difficulty    VARCHAR(50),
    price         NUMERIC(12,2),
    currency      VARCHAR(10)     DEFAULT 'INR',
    rating        NUMERIC(3,1)    DEFAULT 0,
    review_count  INTEGER         DEFAULT 0,
    highlights    TEXT,
    best_time     VARCHAR(200),
    status        VARCHAR(20)     DEFAULT 'draft',
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_activities_slug   ON activities(slug);
CREATE INDEX idx_activities_status ON activities(status);

-- ============================================================
-- 10. BLOGS
-- ============================================================
CREATE TABLE blogs (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(300)    NOT NULL,
    slug          VARCHAR(320)    NOT NULL UNIQUE,
    category      VARCHAR(100),
    author        VARCHAR(100),
    excerpt       TEXT,
    content       TEXT,
    image         VARCHAR(500),
    read_time     VARCHAR(50),
    status        VARCHAR(20)     DEFAULT 'draft',
    published_at  TIMESTAMP,
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_blogs_slug   ON blogs(slug);
CREATE INDEX idx_blogs_status ON blogs(status);

-- ============================================================
-- 11. BOOKINGS
-- ============================================================
CREATE TABLE bookings (
    id             BIGSERIAL PRIMARY KEY,
    booking_ref    VARCHAR(30)     NOT NULL UNIQUE,
    user_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id     BIGINT          REFERENCES packages(id),
    package_title  VARCHAR(200),
    travel_date    DATE,
    end_date       DATE,
    travelers      INTEGER         DEFAULT 1,
    total_amount   NUMERIC(12,2),
    status         VARCHAR(30)     DEFAULT 'pending',
    payment_status VARCHAR(30)     DEFAULT 'unpaid',
    created_at     TIMESTAMP       DEFAULT NOW(),
    updated_at     TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_bookings_user    ON bookings(user_id);
CREATE INDEX idx_bookings_package ON bookings(package_id);
CREATE INDEX idx_bookings_status  ON bookings(status);

-- ============================================================
-- 12. ENQUIRIES
-- ============================================================
CREATE TABLE enquiries (
    id             BIGSERIAL PRIMARY KEY,
    enquiry_ref    VARCHAR(30)     NOT NULL UNIQUE,
    user_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id     BIGINT          REFERENCES packages(id),
    package_title  VARCHAR(200),
    destination    VARCHAR(200),
    travel_date    DATE,
    travelers      INTEGER         DEFAULT 1,
    budget         VARCHAR(30),
    message        TEXT,
    status         VARCHAR(30)     DEFAULT 'pending',
    admin_notes    TEXT,
    confirmed_at   TIMESTAMP,
    created_at     TIMESTAMP       DEFAULT NOW(),
    updated_at     TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_enquiries_user    ON enquiries(user_id);
CREATE INDEX idx_enquiries_package ON enquiries(package_id);
CREATE INDEX idx_enquiries_status  ON enquiries(status);

-- ============================================================
-- 13. LEADS
-- ============================================================
CREATE TABLE leads (
    id            BIGSERIAL PRIMARY KEY,
    lead_type     VARCHAR(50)     DEFAULT 'general',
    name          VARCHAR(150)    NOT NULL,
    email         VARCHAR(150),
    phone         VARCHAR(30),
    whatsapp      VARCHAR(30),
    source_url    VARCHAR(500),
    destination   VARCHAR(200),
    travel_date   DATE,
    travelers     INTEGER         DEFAULT 1,
    budget        VARCHAR(100),
    message       TEXT,
    status        VARCHAR(20)     DEFAULT 'new',
    priority      VARCHAR(20)     DEFAULT 'normal',
    assigned_to   BIGINT          REFERENCES users(id),
    follow_up_at  TIMESTAMP,
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_leads_status  ON leads(status);
CREATE INDEX idx_leads_email   ON leads(email);

-- ============================================================
-- 14. REVIEWS
-- ============================================================
CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id  BIGINT    NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    rating      INTEGER   DEFAULT 5,
    comment     TEXT,
    images      TEXT,     -- JSON array of image URLs
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reviews_package ON reviews(package_id);
CREATE INDEX idx_reviews_user    ON reviews(user_id);

-- ============================================================
-- 15. WISHLISTS
-- ============================================================
CREATE TABLE wishlists (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id  BIGINT    NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    created_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, package_id)
);

CREATE INDEX idx_wishlists_user    ON wishlists(user_id);
CREATE INDEX idx_wishlists_package ON wishlists(package_id);

-- ============================================================
-- 16. TESTIMONIALS
-- ============================================================
CREATE TABLE testimonials (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    location    VARCHAR(150),
    photo       VARCHAR(500),
    quote       TEXT,
    rating      NUMERIC(3,1)  DEFAULT 5,
    status      VARCHAR(20)   DEFAULT 'published',
    created_at  TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 17. FAQS
-- ============================================================
CREATE TABLE faqs (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    question    TEXT          NOT NULL,
    answer      TEXT          NOT NULL,
    sort_order  INTEGER       DEFAULT 0,
    status      VARCHAR(20)   DEFAULT 'published',
    created_at  TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 18. SITE SETTINGS (Key-Value)
-- ============================================================
CREATE TABLE site_settings (
    id         BIGSERIAL PRIMARY KEY,
    key        VARCHAR(100)  NOT NULL UNIQUE,
    value      TEXT,
    updated_at TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 19. MENUS (CMS Pages)
-- ============================================================
CREATE TABLE menus (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    description     VARCHAR(300),
    icon            VARCHAR(500),
    display_order   INTEGER       DEFAULT 0,
    status          VARCHAR(30)   DEFAULT 'published',
    page_title      VARCHAR(200),
    page_subtitle   VARCHAR(500),
    page_hero_image VARCHAR(500),
    page_content    TEXT,
    seo_title       VARCHAR(200),
    seo_description VARCHAR(500),
    seo_keywords    VARCHAR(500),
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 20. MENU_DESTINATIONS (Many-to-Many Join Table)
-- ============================================================
CREATE TABLE menu_destinations (
    menu_id         BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    destination_id  BIGINT NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_id, destination_id)
);

CREATE INDEX idx_menu_destinations_menu ON menu_destinations(menu_id);
CREATE INDEX idx_menu_destinations_dest ON menu_destinations(destination_id);

-- ============================================================
-- 21. OTP VERIFICATIONS
-- ============================================================
CREATE TABLE otp_verifications (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(150)  NOT NULL,
    code          VARCHAR(10)   NOT NULL,  -- legacy rows only; new OTPs are stored as code_hash
    code_hash     VARCHAR(64),             -- salted BCrypt; preferred storage
    transaction_id VARCHAR(64),            -- server-bound authentication transaction
    purpose       VARCHAR(50)   NOT NULL,  -- edit_enquiry, delete_enquiry, edit_lead, delete_lead, login_email, login_phone, forgot_password
    record_id     BIGINT        NOT NULL,
    record_type   VARCHAR(20)   NOT NULL,  -- enquiry or lead
    expires_at    TIMESTAMP     NOT NULL,
    verified      BOOLEAN       DEFAULT FALSE,
    attempts      INTEGER       DEFAULT 0,
    max_attempts  INTEGER       DEFAULT 5,
    created_at    TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_otp_email     ON otp_verifications(email);
CREATE INDEX idx_otp_purpose   ON otp_verifications(purpose, record_type, record_id);

-- ============================================================
-- 22. EDIT SESSIONS (3-hour edit window)
-- ============================================================
CREATE TABLE edit_sessions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    record_id    BIGINT        NOT NULL,
    record_type  VARCHAR(20)   NOT NULL,  -- enquiry or lead
    purpose      VARCHAR(50)   NOT NULL,  -- edit or delete
    expires_at   TIMESTAMP     NOT NULL,
    is_active    BOOLEAN       DEFAULT TRUE,
    created_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_edit_sessions_user   ON edit_sessions(user_id);
CREATE INDEX idx_edit_sessions_record ON edit_sessions(record_type, record_id);

-- ============================================================
-- 23. ADMIN AUDIT LOGS
-- ============================================================
CREATE TABLE admin_audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    admin_id     BIGINT        NOT NULL,
    admin_email  VARCHAR(150)  NOT NULL,
    action       VARCHAR(50)   NOT NULL,  -- delete_lead, delete_enquiry
    record_id    BIGINT        NOT NULL,
    record_type  VARCHAR(20)   NOT NULL,  -- lead or enquiry
    record_data  TEXT,                     -- JSON snapshot of deleted record
    reason       TEXT,
    created_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_audit_admin   ON admin_audit_logs(admin_id);
CREATE INDEX idx_audit_action  ON admin_audit_logs(action);

-- ============================================================
-- SEED DATA: Admin User
-- ============================================================
-- Password: admin123 (BCrypt encoded)
INSERT INTO users (name, email, phone, password_hash, role_id, is_active)
VALUES (
    'Admin',
    'admin@travelvista.com',
    '+91 98765 43210',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    (SELECT id FROM roles WHERE name = 'super_admin'),
    TRUE
);

-- ============================================================
-- SEED DATA: Countries
-- ============================================================
INSERT INTO countries (name, slug, code, is_indian, status, featured, sort_order) VALUES
    ('India',             'india',             'IN', TRUE,  'published', TRUE,  1),
    ('United Arab Emirates', 'uae',            'AE', FALSE, 'published', TRUE,  2),
    ('Thailand',          'thailand',          'TH', FALSE, 'published', TRUE,  3),
    ('Singapore',         'singapore',         'SG', FALSE, 'published', TRUE,  4),
    ('Malaysia',          'malaysia',          'MY', FALSE, 'published', TRUE,  5),
    ('Maldives',          'maldives',          'MV', FALSE, 'published', TRUE,  6),
    ('Indonesia',         'indonesia',         'ID', FALSE, 'published', TRUE,  7),
    ('Vietnam',           'vietnam',           'VN', FALSE, 'published', TRUE,  8),
    ('Japan',             'japan',             'JP', FALSE, 'published', TRUE,  9),
    ('Switzerland',       'switzerland',       'CH', FALSE, 'published', TRUE, 10),
    ('Sri Lanka',         'sri-lanka',         'LK', FALSE, 'published', TRUE, 11),
    ('Australia',         'australia',         'AU', FALSE, 'published', TRUE, 12),
    ('New Zealand',       'new-zealand',       'NZ', FALSE, 'published', TRUE, 13),
    ('USA',               'usa',               'US', FALSE, 'published', TRUE, 14),
    ('Nepal',             'nepal',             'NP', FALSE, 'published', TRUE, 15),
    ('Bhutan',            'bhutan',            'BT', FALSE, 'published', TRUE, 16);

-- ============================================================
-- SEED DATA: States (India)
-- ============================================================
INSERT INTO states (name, slug, country_id, status, featured, sort_order) VALUES
    ('Gujarat',       'gujarat',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  1),
    ('Rajasthan',     'rajasthan',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  2),
    ('Kerala',        'kerala',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  3),
    ('Goa',           'goa',           (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  4),
    ('Maharashtra',   'maharashtra',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  5),
    ('Himachal Pradesh', 'himachal-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 6),
    ('Jammu & Kashmir', 'jammu-kashmir', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 7),
    ('Uttarakhand',   'uttarakhand',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  8),
    ('Karnataka',     'karnataka',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  9),
    ('Tamil Nadu',    'tamil-nadu',    (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 10),
    ('West Bengal',   'west-bengal',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 11),
    ('Sikkim',        'sikkim',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 12),
    ('Meghalaya',     'meghalaya',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 13),
    ('Assam',         'assam',         (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 14),
    ('Arunachal Pradesh', 'arunachal-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 15),
    ('Nagaland',      'nagaland',      (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 16),
    ('Manipur',       'manipur',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 17),
    ('Mizoram',       'mizoram',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 18),
    ('Tripura',       'tripura',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 19),
    ('Madhya Pradesh','madhya-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 20),
    ('Uttar Pradesh', 'uttar-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 21),
    ('Bihar',         'bihar',         (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 22),
    ('Odisha',        'odisha',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 23),
    ('Chhattisgarh',  'chhattisgarh',  (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 24),
    ('Punjab',        'punjab',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 25),
    ('Haryana',       'haryana',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 26),
    ('Jharkhand',     'jharkhand',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 27),
    ('Andhra Pradesh','andhra-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 28),
    ('Telangana',     'telangana',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 29),
    ('Chandigarh',    'chandigarh',    (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 30);

-- ============================================================
-- SEED DATA: Destinations
-- ============================================================
INSERT INTO destinations (name, slug, state, country, type, short_description, tagline, status, featured, sort_order) VALUES
    ('Rann of Kutch',       'rann-of-kutch',       'Gujarat',     'India', 'domestic', 'The white salt desert of Gujarat', 'Endless White Horizons', 'published', TRUE, 1),
    ('Jaipur',              'jaipur',               'Rajasthan',   'India', 'domestic', 'The Pink City of India', 'Royalty Lives Here', 'published', TRUE, 2),
    ('Alleppey',            'alleppey',             'Kerala',      'India', 'domestic', 'Venice of the East', 'Backwaters & Beyond', 'published', TRUE, 3),
    ('Goa Beaches',         'goa-beaches',          'Goa',         'India', 'domestic', 'Sun, Sand and Surf', 'Beach Paradise', 'published', TRUE, 4),
    ('Darjeeling',          'darjeeling',           'West Bengal', 'India', 'domestic', 'Queen of the Himalayas', 'Tea Gardens & Mountain Views', 'published', TRUE, 5),
    ('Havelock Island',     'havelock-island',      'Andaman',     'India', 'domestic', 'Pristine tropical paradise', 'Crystal Waters Await', 'published', TRUE, 6),
    ('Varanasi',            'varanasi',             'Uttar Pradesh','India', 'domestic', 'Spiritual capital of India', 'Where Time Stands Still', 'published', TRUE, 7),
    ('Ladakh',              'ladakh',               'Jammu & Kashmir','India', 'domestic', 'Land of High Passes', 'Roof of the World', 'published', TRUE, 8),
    ('Manali',              'manali',               'Himachal Pradesh','India', 'domestic', 'Valley of Gods', 'Adventure & Romance', 'published', TRUE, 9),
    ('Dubai',               'dubai',                'Dubai',       'UAE',  'international', 'City of Gold', 'Where Dreams Touch the Sky', 'published', TRUE, 10),
    ('Bali',                'bali',                 'Bali',        'Indonesia', 'international', 'Island of the Gods', 'Paradise Found', 'published', TRUE, 11),
    ('Bangkok',             'bangkok',              'Bangkok',     'Thailand', 'international', 'City of Angels', 'Vibrant & Exotic', 'published', TRUE, 12),
    ('Maldives',            'maldives',             'Malé',        'Maldives', 'international', 'Tropical paradise in the Indian Ocean', 'Overwater Bliss', 'published', TRUE, 13),
    ('Singapore',           'singapore',            'Singapore',   'Singapore', 'international', 'The Lion City', 'Where East Meets West', 'published', TRUE, 14),
    ('Tokyo',               'tokyo',                'Tokyo',       'Japan', 'international', 'The Electric Metropolis', 'Tradition Meets Future', 'published', TRUE, 15);

-- ============================================================
-- SEED DATA: Packages
-- ============================================================
INSERT INTO packages (title, slug, description, short_description, destination, state, country, duration_days, duration_nights, starting_price, category, tags, status, featured) VALUES
    ('Kerala Backwater & Beaches', 'kerala-backwater-beaches',
     'Experience the serene backwaters of Alleppey and the pristine beaches of Kovalam in this 5-day Kerala tour package.',
     'Explore Kerala backwaters, beaches, and lush green landscapes',
     'Alleppey', 'Kerala', 'India', 5, 4, 12499, 'domestic',
     'honeymoon,romantic,beach,backwater,kerala,couple',
     'published', TRUE),

    ('Goa Beach Holiday', 'goa-beach-holiday',
     'Enjoy the vibrant beaches, nightlife, and Portuguese heritage of Goa in this 4-day getaway.',
     'Sun, sand, and nightlife in Goa',
     'Goa', 'Goa', 'India', 4, 3, 8999, 'domestic',
     'beach,honeymoon,couple,nightlife,goa,budget',
     'published', TRUE),

    ('Rajasthan Royal Tour', 'rajasthan-royal-tour',
     'Explore the majestic forts, palaces, and desert landscapes of Rajasthan in this 7-day royal tour.',
     'Discover the royalty of Rajasthan',
     'Jaipur', 'Rajasthan', 'India', 7, 6, 14999, 'domestic',
     'heritage,fort,palace,desert,rajasthan,royal,culture',
     'published', TRUE),

    ('Swiss Alps Adventure', 'swiss-alps-adventure',
     'Experience the breathtaking Swiss Alps with stunning mountain views, pristine lakes, and charming villages.',
     'Majestic Swiss Alps adventure',
     'Swiss Alps', NULL, 'Switzerland', 8, 7, 185000, 'international',
     'adventure,mountain,luxury,honeymoon,switzerland,europe',
     'published', TRUE),

    ('Thailand Explorer', 'thailand-explorer',
     'Discover the beauty of Thailand from Bangkok temples to Phuket beaches.',
     'Explore temples, beaches, and nightlife in Thailand',
     'Bangkok', NULL, 'Thailand', 6, 5, 45000, 'international',
     'beach,adventure,temple,thailand,bangkok,phuket,couple',
     'published', TRUE),

    ('Dubai Luxury Experience', 'dubai-luxury-experience',
     'Experience the luxury and adventure of Dubai with desert safari, Burj Khalifa, and world-class shopping.',
     'Luxury living in the City of Gold',
     'Dubai', NULL, 'UAE', 5, 4, 75000, 'international',
     'luxury,dubai,desert,shopping,family,adventure',
     'published', TRUE),

    ('Andaman Island Escape', 'andaman-island-escape',
     'Discover the pristine beaches, crystal-clear waters, and vibrant coral reefs of Andaman Islands.',
     'Tropical island paradise adventure',
     'Havelock Island', 'Andaman', 'India', 5, 4, 18999, 'domestic',
     'beach,island,scuba,snorkeling,andaman,adventure,honeymoon',
     'published', TRUE),

    ('North East Explorer', 'north-east-explorer',
     'Explore the unexplored beauty of North East India with tea gardens, living root bridges, and wildlife.',
     'Discover India hidden gem',
     'Gangtok', 'Sikkim', 'India', 7, 6, 22000, 'domestic',
     'mountain,adventure,northeast,culture,wildlife,trekking',
     'published', TRUE);

-- ============================================================
-- SEED DATA: Site Settings
-- ============================================================
INSERT INTO site_settings (key, value) VALUES
    ('site_name',       'TravelVista'),
    ('site_tagline',    'Explore the Incredible India'),
    ('site_email',      'info@travelvista.com'),
    ('site_phone',      '+91 98765 43210'),
    ('site_whatsapp',   '+91 98765 43210'),
    ('site_address',    'Mumbai, Maharashtra, India'),
    ('facebook_url',    'https://facebook.com/travelvista'),
    ('instagram_url',   'https://instagram.com/travelvista'),
    ('youtube_url',     'https://youtube.com/travelvista'),
    ('twitter_url',     'https://twitter.com/travelvista'),
    ('hero_title',      'Explore the Incredible India'),
    ('hero_subtitle',   'Discover breathtaking destinations, curated packages, and unforgettable experiences'),
    ('about_title',     'About TravelVista'),
    ('about_content',   'Your trusted travel partner since 2020'),
    ('footer_text',     '© 2026 TravelVista. All rights reserved.'),
    ('meta_title',      'TravelVista - Explore India & Beyond'),
    ('meta_description','Book curated travel packages, hotels, and experiences across India and internationally'),
    ('analytics_id',    '');

-- ============================================================
-- SEED DATA: Testimonials
-- ============================================================
INSERT INTO testimonials (name, location, quote, rating, status) VALUES
    ('Priya Sharma',  'Mumbai',     'Amazing experience! The Kerala backwater trip was perfectly organized. Will book again!', 5, 'published'),
    ('Rahul Patel',   'Ahmedabad',  'Rajasthan tour was a dream come true. Every detail was taken care of.', 5, 'published'),
    ('Anita Desai',   'Delhi',      'Goa beach holiday was fantastic. Great value for money and wonderful service.', 4, 'published'),
    ('Vikram Singh',  'Bangalore',  'The Swiss Alps package was beyond our expectations. Truly premium experience.', 5, 'published'),
    ('Sneha Reddy',   'Hyderabad',  'Andaman trip was magical! Crystal clear waters and pristine beaches.', 5, 'published');

-- ============================================================
-- END OF SCHEMA
-- Total: 23 tables
--   1.  roles
--   2.  users
--   3.  countries
--   4.  states
--   5.  cities
--   6.  destinations
--   7.  packages
--   8.  hotels
--   9.  activities
--   10. blogs
--   11. bookings
--   12. enquiries
--   13. leads
--   14. reviews
--   15. wishlists
--   16. testimonials
--   17. faqs
--   18. site_settings
--   19. menus
--   20. menu_destinations (join table)
--   21. otp_verifications (code_hash + auth purposes added)
--   22. edit_sessions
--   23. admin_audit_logs
-- ============================================================


-- Check user ID 11 without exposing password_hash.
SELECT
    u.id,
    u.name,
    u.email,
    u.phone,
    r.name AS role,
    u.is_active,
    u.email_verified,
    u.phone_verified,
    u.created_at,
    u.updated_at
FROM users u
LEFT JOIN roles r ON r.id = u.role_id
WHERE u.id = 11;

-- Existing admin verification query (also excludes password_hash).
SELECT
    u.id,
    u.name,
    u.email,
    u.phone,
    r.name AS role,
    u.is_active,
    u.email_verified,
    u.phone_verified,
    u.created_at,
    u.updated_at
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'super_admin';);
CREATE INDEX idx_users_role     ON users(role_id);

-- ============================================================
-- 3. COUNTRIES
-- ============================================================
CREATE TABLE countries (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    code            VARCHAR(10),                  -- ISO code: IN, US, TH
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    is_indian       BOOLEAN       DEFAULT TRUE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 4. STATES
-- ============================================================
CREATE TABLE states (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    country_id      BIGINT        NOT NULL REFERENCES countries(id),
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_states_country ON states(country_id);

-- ============================================================
-- 5. CITIES
-- ============================================================
CREATE TABLE cities (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    state_id        BIGINT        REFERENCES states(id),
    country_id      BIGINT        REFERENCES countries(id),
    description     TEXT,
    image           TEXT,
    hero_image      TEXT,
    seo_title       VARCHAR(200),
    seo_description TEXT,
    sort_order      INTEGER       DEFAULT 0,
    status          VARCHAR(20)   DEFAULT 'published',
    featured        BOOLEAN       DEFAULT FALSE,
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_cities_state   ON cities(state_id);
CREATE INDEX idx_cities_country ON cities(country_id);

-- ============================================================
-- 6. DESTINATIONS
-- ============================================================
CREATE TABLE destinations (
    id                        BIGSERIAL PRIMARY KEY,
    name                      VARCHAR(200)  NOT NULL,
    slug                      VARCHAR(220)  NOT NULL UNIQUE,
    country                   VARCHAR(100),
    state                     VARCHAR(100),
    type                      VARCHAR(100)  DEFAULT 'domestic',
    description               TEXT,
    short_description         VARCHAR(500),
    image                     VARCHAR(500),
    tagline                   VARCHAR(300),
    best_time                 VARCHAR(100),
    avg_temp                  VARCHAR(100),
    languages                 VARCHAR(200),
    highlights                TEXT,
    package_count             INTEGER       DEFAULT 0,
    status                    VARCHAR(20)   DEFAULT 'draft',
    featured                  BOOLEAN       DEFAULT FALSE,
    sort_order                INTEGER       DEFAULT 0,

    -- CMS Hero section
    hero_images               TEXT,
    hero_title                VARCHAR(200),
    hero_subtitle             VARCHAR(500),
    hero_cta_text             VARCHAR(100),
    hero_cta_url              VARCHAR(500),

    -- CMS About section
    about_title               VARCHAR(200),
    about_content             TEXT,
    about_image               VARCHAR(500),
    about_image_position      VARCHAR(20)   DEFAULT 'right',

    -- CMS JSON sections
    attractions               TEXT,         -- JSON array
    experiences               TEXT,         -- JSON array
    destination_highlights    TEXT,         -- JSON array
    quick_info                TEXT,         -- JSON array

    -- SEO
    seo_title                 VARCHAR(200),
    seo_description           VARCHAR(500),
    og_title                  VARCHAR(200),
    og_description            VARCHAR(500),
    og_image                  VARCHAR(500),
    canonical_url             VARCHAR(500),
    no_index                  BOOLEAN       DEFAULT FALSE,

    -- Section visibility toggles
    show_attractions          BOOLEAN       DEFAULT TRUE,
    show_experiences          BOOLEAN       DEFAULT TRUE,
    show_highlights           BOOLEAN       DEFAULT TRUE,
    show_packages             BOOLEAN       DEFAULT TRUE,
    show_quick_info           BOOLEAN       DEFAULT TRUE,

    -- Coming soon
    packages_coming_soon      BOOLEAN       DEFAULT FALSE,
    packages_coming_soon_text VARCHAR(500),

    created_at                TIMESTAMP     DEFAULT NOW(),
    updated_at                TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_destinations_slug   ON destinations(slug);
CREATE INDEX idx_destinations_state  ON destinations(state);
CREATE INDEX idx_destinations_type   ON destinations(type);
CREATE INDEX idx_destinations_status ON destinations(status);

-- ============================================================
-- 7. PACKAGES (Travel Packages)
-- ============================================================
CREATE TABLE packages (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(200)    NOT NULL,
    slug               VARCHAR(220)    NOT NULL UNIQUE,
    description        TEXT,
    short_description  VARCHAR(500),
    destination        VARCHAR(200),
    state              VARCHAR(100),
    country            VARCHAR(100),
    duration_days      INTEGER         DEFAULT 0,
    duration_nights    INTEGER         DEFAULT 0,
    starting_price     NUMERIC(12,2),
    currency           VARCHAR(10)     DEFAULT 'INR',
    cover_image        VARCHAR(500),
    highlights         TEXT,
    inclusions         TEXT,
    exclusions         TEXT,
    itinerary          JSONB,          -- PostgreSQL JSONB
    category           VARCHAR(50)     DEFAULT 'domestic',
    tags               VARCHAR(255),
    status             VARCHAR(20)     DEFAULT 'draft',
    featured           BOOLEAN         DEFAULT FALSE,
    rating             NUMERIC(3,1)    DEFAULT 0,
    review_count       INTEGER         DEFAULT 0,
    created_at         TIMESTAMP       DEFAULT NOW(),
    updated_at         TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_packages_slug       ON packages(slug);
CREATE INDEX idx_packages_status     ON packages(status);
CREATE INDEX idx_packages_category   ON packages(category);
CREATE INDEX idx_packages_state      ON packages(state);
CREATE INDEX idx_packages_destination ON packages(destination);
CREATE INDEX idx_packages_featured   ON packages(featured) WHERE featured = TRUE;
CREATE INDEX idx_packages_tags       ON packages USING GIN (to_tsvector('english', COALESCE(tags, '')));

-- ============================================================
-- 8. HOTELS
-- ============================================================
CREATE TABLE hotels (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200)    NOT NULL,
    slug             VARCHAR(220)    NOT NULL UNIQUE,
    location         VARCHAR(200),
    city             VARCHAR(100),
    country          VARCHAR(100),
    description      TEXT,
    image            VARCHAR(500),
    category         VARCHAR(50),
    rating           NUMERIC(3,1)    DEFAULT 0,
    review_count     INTEGER         DEFAULT 0,
    price_per_night  NUMERIC(12,2),
    currency         VARCHAR(10)     DEFAULT 'INR',
    amenities        TEXT,            -- JSON array
    room_types       TEXT,            -- JSON array
    status           VARCHAR(20)     DEFAULT 'draft',
    featured         BOOLEAN         DEFAULT FALSE,
    created_at       TIMESTAMP       DEFAULT NOW(),
    updated_at       TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_hotels_slug   ON hotels(slug);
CREATE INDEX idx_hotels_status ON hotels(status);

-- ============================================================
-- 9. ACTIVITIES
-- ============================================================
CREATE TABLE activities (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200)    NOT NULL,
    slug          VARCHAR(220)    NOT NULL UNIQUE,
    location      VARCHAR(200),
    category      VARCHAR(100),
    description   TEXT,
    image         VARCHAR(500),
    duration      VARCHAR(50),
    difficulty    VARCHAR(50),
    price         NUMERIC(12,2),
    currency      VARCHAR(10)     DEFAULT 'INR',
    rating        NUMERIC(3,1)    DEFAULT 0,
    review_count  INTEGER         DEFAULT 0,
    highlights    TEXT,
    best_time     VARCHAR(200),
    status        VARCHAR(20)     DEFAULT 'draft',
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_activities_slug   ON activities(slug);
CREATE INDEX idx_activities_status ON activities(status);

-- ============================================================
-- 10. BLOGS
-- ============================================================
CREATE TABLE blogs (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(300)    NOT NULL,
    slug          VARCHAR(320)    NOT NULL UNIQUE,
    category      VARCHAR(100),
    author        VARCHAR(100),
    excerpt       TEXT,
    content       TEXT,
    image         VARCHAR(500),
    read_time     VARCHAR(50),
    status        VARCHAR(20)     DEFAULT 'draft',
    published_at  TIMESTAMP,
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_blogs_slug   ON blogs(slug);
CREATE INDEX idx_blogs_status ON blogs(status);

-- ============================================================
-- 11. BOOKINGS
-- ============================================================
CREATE TABLE bookings (
    id             BIGSERIAL PRIMARY KEY,
    booking_ref    VARCHAR(30)     NOT NULL UNIQUE,
    user_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id     BIGINT          REFERENCES packages(id),
    package_title  VARCHAR(200),
    travel_date    DATE,
    end_date       DATE,
    travelers      INTEGER         DEFAULT 1,
    total_amount   NUMERIC(12,2),
    status         VARCHAR(30)     DEFAULT 'pending',
    payment_status VARCHAR(30)     DEFAULT 'unpaid',
    created_at     TIMESTAMP       DEFAULT NOW(),
    updated_at     TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_bookings_user    ON bookings(user_id);
CREATE INDEX idx_bookings_package ON bookings(package_id);
CREATE INDEX idx_bookings_status  ON bookings(status);

-- ============================================================
-- 12. ENQUIRIES
-- ============================================================
CREATE TABLE enquiries (
    id             BIGSERIAL PRIMARY KEY,
    enquiry_ref    VARCHAR(30)     NOT NULL UNIQUE,
    user_id        BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id     BIGINT          REFERENCES packages(id),
    package_title  VARCHAR(200),
    destination    VARCHAR(200),
    travel_date    DATE,
    travelers      INTEGER         DEFAULT 1,
    budget         VARCHAR(30),
    message        TEXT,
    status         VARCHAR(30)     DEFAULT 'pending',
    admin_notes    TEXT,
    confirmed_at   TIMESTAMP,
    created_at     TIMESTAMP       DEFAULT NOW(),
    updated_at     TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_enquiries_user    ON enquiries(user_id);
CREATE INDEX idx_enquiries_package ON enquiries(package_id);
CREATE INDEX idx_enquiries_status  ON enquiries(status);

-- ============================================================
-- 13. LEADS
-- ============================================================
CREATE TABLE leads (
    id            BIGSERIAL PRIMARY KEY,
    lead_type     VARCHAR(50)     DEFAULT 'general',
    name          VARCHAR(150)    NOT NULL,
    email         VARCHAR(150),
    phone         VARCHAR(30),
    whatsapp      VARCHAR(30),
    source_url    VARCHAR(500),
    destination   VARCHAR(200),
    travel_date   DATE,
    travelers     INTEGER         DEFAULT 1,
    budget        VARCHAR(100),
    message       TEXT,
    status        VARCHAR(20)     DEFAULT 'new',
    priority      VARCHAR(20)     DEFAULT 'normal',
    assigned_to   BIGINT          REFERENCES users(id),
    follow_up_at  TIMESTAMP,
    created_at    TIMESTAMP       DEFAULT NOW(),
    updated_at    TIMESTAMP       DEFAULT NOW()
);

CREATE INDEX idx_leads_status  ON leads(status);
CREATE INDEX idx_leads_email   ON leads(email);

-- ============================================================
-- 14. REVIEWS
-- ============================================================
CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id  BIGINT    NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    rating      INTEGER   DEFAULT 5,
    comment     TEXT,
    images      TEXT,     -- JSON array of image URLs
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reviews_package ON reviews(package_id);
CREATE INDEX idx_reviews_user    ON reviews(user_id);

-- ============================================================
-- 15. WISHLISTS
-- ============================================================
CREATE TABLE wishlists (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id  BIGINT    NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    created_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, package_id)
);

CREATE INDEX idx_wishlists_user    ON wishlists(user_id);
CREATE INDEX idx_wishlists_package ON wishlists(package_id);

-- ============================================================
-- 16. TESTIMONIALS
-- ============================================================
CREATE TABLE testimonials (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    location    VARCHAR(150),
    photo       VARCHAR(500),
    quote       TEXT,
    rating      NUMERIC(3,1)  DEFAULT 5,
    status      VARCHAR(20)   DEFAULT 'published',
    created_at  TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 17. FAQS
-- ============================================================
CREATE TABLE faqs (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    question    TEXT          NOT NULL,
    answer      TEXT          NOT NULL,
    sort_order  INTEGER       DEFAULT 0,
    status      VARCHAR(20)   DEFAULT 'published',
    created_at  TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 18. SITE SETTINGS (Key-Value)
-- ============================================================
CREATE TABLE site_settings (
    id         BIGSERIAL PRIMARY KEY,
    key        VARCHAR(100)  NOT NULL UNIQUE,
    value      TEXT,
    updated_at TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 19. MENUS (CMS Pages)
-- ============================================================
CREATE TABLE menus (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    slug            VARCHAR(120)  NOT NULL UNIQUE,
    description     VARCHAR(300),
    icon            VARCHAR(500),
    display_order   INTEGER       DEFAULT 0,
    status          VARCHAR(30)   DEFAULT 'published',
    page_title      VARCHAR(200),
    page_subtitle   VARCHAR(500),
    page_hero_image VARCHAR(500),
    page_content    TEXT,
    seo_title       VARCHAR(200),
    seo_description VARCHAR(500),
    seo_keywords    VARCHAR(500),
    created_at      TIMESTAMP     DEFAULT NOW(),
    updated_at      TIMESTAMP     DEFAULT NOW()
);

-- ============================================================
-- 20. MENU_DESTINATIONS (Many-to-Many Join Table)
-- ============================================================
CREATE TABLE menu_destinations (
    menu_id         BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    destination_id  BIGINT NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_id, destination_id)
);

CREATE INDEX idx_menu_destinations_menu ON menu_destinations(menu_id);
CREATE INDEX idx_menu_destinations_dest ON menu_destinations(destination_id);

-- ============================================================
-- 21. OTP VERIFICATIONS
-- ============================================================
CREATE TABLE otp_verifications (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(150)  NOT NULL,
    code          VARCHAR(10)   NOT NULL,  -- legacy rows only; new OTPs are stored as code_hash
    code_hash     VARCHAR(64),             -- SHA-256(otp + identifier); preferred storage
    transaction_id VARCHAR(64),            -- server-bound authentication transaction
    purpose       VARCHAR(50)   NOT NULL,  -- edit_enquiry, delete_enquiry, edit_lead, delete_lead, login_email, login_phone, forgot_password
    record_id     BIGINT        NOT NULL,
    record_type   VARCHAR(20)   NOT NULL,  -- enquiry or lead
    expires_at    TIMESTAMP     NOT NULL,
    verified      BOOLEAN       DEFAULT FALSE,
    attempts      INTEGER       DEFAULT 0,
    max_attempts  INTEGER       DEFAULT 5,
    created_at    TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_otp_email     ON otp_verifications(email);
CREATE INDEX idx_otp_purpose   ON otp_verifications(purpose, record_type, record_id);

-- ============================================================
-- 22. EDIT SESSIONS (3-hour edit window)
-- ============================================================
CREATE TABLE edit_sessions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    record_id    BIGINT        NOT NULL,
    record_type  VARCHAR(20)   NOT NULL,  -- enquiry or lead
    purpose      VARCHAR(50)   NOT NULL,  -- edit or delete
    expires_at   TIMESTAMP     NOT NULL,
    is_active    BOOLEAN       DEFAULT TRUE,
    created_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_edit_sessions_user   ON edit_sessions(user_id);
CREATE INDEX idx_edit_sessions_record ON edit_sessions(record_type, record_id);

-- ============================================================
-- 23. ADMIN AUDIT LOGS
-- ============================================================
CREATE TABLE admin_audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    admin_id     BIGINT        NOT NULL,
    admin_email  VARCHAR(150)  NOT NULL,
    action       VARCHAR(50)   NOT NULL,  -- delete_lead, delete_enquiry
    record_id    BIGINT        NOT NULL,
    record_type  VARCHAR(20)   NOT NULL,  -- lead or enquiry
    record_data  TEXT,                     -- JSON snapshot of deleted record
    reason       TEXT,
    created_at   TIMESTAMP     DEFAULT NOW()
);

CREATE INDEX idx_audit_admin   ON admin_audit_logs(admin_id);
CREATE INDEX idx_audit_action  ON admin_audit_logs(action);

-- ============================================================
-- SEED DATA: Admin User
-- ============================================================
-- Password: admin123 (BCrypt encoded)
INSERT INTO users (name, email, phone, password_hash, role_id, is_active)
VALUES (
    'Admin',
    'admin@travelvista.com',
    '+91 98765 43210',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    (SELECT id FROM roles WHERE name = 'super_admin'),
    TRUE
);

-- ============================================================
-- SEED DATA: Countries
-- ============================================================
INSERT INTO countries (name, slug, code, is_indian, status, featured, sort_order) VALUES
    ('India',             'india',             'IN', TRUE,  'published', TRUE,  1),
    ('United Arab Emirates', 'uae',            'AE', FALSE, 'published', TRUE,  2),
    ('Thailand',          'thailand',          'TH', FALSE, 'published', TRUE,  3),
    ('Singapore',         'singapore',         'SG', FALSE, 'published', TRUE,  4),
    ('Malaysia',          'malaysia',          'MY', FALSE, 'published', TRUE,  5),
    ('Maldives',          'maldives',          'MV', FALSE, 'published', TRUE,  6),
    ('Indonesia',         'indonesia',         'ID', FALSE, 'published', TRUE,  7),
    ('Vietnam',           'vietnam',           'VN', FALSE, 'published', TRUE,  8),
    ('Japan',             'japan',             'JP', FALSE, 'published', TRUE,  9),
    ('Switzerland',       'switzerland',       'CH', FALSE, 'published', TRUE, 10),
    ('Sri Lanka',         'sri-lanka',         'LK', FALSE, 'published', TRUE, 11),
    ('Australia',         'australia',         'AU', FALSE, 'published', TRUE, 12),
    ('New Zealand',       'new-zealand',       'NZ', FALSE, 'published', TRUE, 13),
    ('USA',               'usa',               'US', FALSE, 'published', TRUE, 14),
    ('Nepal',             'nepal',             'NP', FALSE, 'published', TRUE, 15),
    ('Bhutan',            'bhutan',            'BT', FALSE, 'published', TRUE, 16);

-- ============================================================
-- SEED DATA: States (India)
-- ============================================================
INSERT INTO states (name, slug, country_id, status, featured, sort_order) VALUES
    ('Gujarat',       'gujarat',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  1),
    ('Rajasthan',     'rajasthan',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  2),
    ('Kerala',        'kerala',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  3),
    ('Goa',           'goa',           (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  4),
    ('Maharashtra',   'maharashtra',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  5),
    ('Himachal Pradesh', 'himachal-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 6),
    ('Jammu & Kashmir', 'jammu-kashmir', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 7),
    ('Uttarakhand',   'uttarakhand',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  8),
    ('Karnataka',     'karnataka',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE,  9),
    ('Tamil Nadu',    'tamil-nadu',    (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 10),
    ('West Bengal',   'west-bengal',   (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 11),
    ('Sikkim',        'sikkim',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 12),
    ('Meghalaya',     'meghalaya',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 13),
    ('Assam',         'assam',         (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 14),
    ('Arunachal Pradesh', 'arunachal-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 15),
    ('Nagaland',      'nagaland',      (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 16),
    ('Manipur',       'manipur',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 17),
    ('Mizoram',       'mizoram',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 18),
    ('Tripura',       'tripura',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 19),
    ('Madhya Pradesh','madhya-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 20),
    ('Uttar Pradesh', 'uttar-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 21),
    ('Bihar',         'bihar',         (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 22),
    ('Odisha',        'odisha',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 23),
    ('Chhattisgarh',  'chhattisgarh',  (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 24),
    ('Punjab',        'punjab',        (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 25),
    ('Haryana',       'haryana',       (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 26),
    ('Jharkhand',     'jharkhand',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 27),
    ('Andhra Pradesh','andhra-pradesh', (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 28),
    ('Telangana',     'telangana',     (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 29),
    ('Chandigarh',    'chandigarh',    (SELECT id FROM countries WHERE slug='india'), 'published', TRUE, 30);

-- ============================================================
-- SEED DATA: Destinations
-- ============================================================
INSERT INTO destinations (name, slug, state, country, type, short_description, tagline, status, featured, sort_order) VALUES
    ('Rann of Kutch',       'rann-of-kutch',       'Gujarat',     'India', 'domestic', 'The white salt desert of Gujarat', 'Endless White Horizons', 'published', TRUE, 1),
    ('Jaipur',              'jaipur',               'Rajasthan',   'India', 'domestic', 'The Pink City of India', 'Royalty Lives Here', 'published', TRUE, 2),
    ('Alleppey',            'alleppey',             'Kerala',      'India', 'domestic', 'Venice of the East', 'Backwaters & Beyond', 'published', TRUE, 3),
    ('Goa Beaches',         'goa-beaches',          'Goa',         'India', 'domestic', 'Sun, Sand and Surf', 'Beach Paradise', 'published', TRUE, 4),
    ('Darjeeling',          'darjeeling',           'West Bengal', 'India', 'domestic', 'Queen of the Himalayas', 'Tea Gardens & Mountain Views', 'published', TRUE, 5),
    ('Havelock Island',     'havelock-island',      'Andaman',     'India', 'domestic', 'Pristine tropical paradise', 'Crystal Waters Await', 'published', TRUE, 6),
    ('Varanasi',            'varanasi',             'Uttar Pradesh','India', 'domestic', 'Spiritual capital of India', 'Where Time Stands Still', 'published', TRUE, 7),
    ('Ladakh',              'ladakh',               'Jammu & Kashmir','India', 'domestic', 'Land of High Passes', 'Roof of the World', 'published', TRUE, 8),
    ('Manali',              'manali',               'Himachal Pradesh','India', 'domestic', 'Valley of Gods', 'Adventure & Romance', 'published', TRUE, 9),
    ('Dubai',               'dubai',                'Dubai',       'UAE',  'international', 'City of Gold', 'Where Dreams Touch the Sky', 'published', TRUE, 10),
    ('Bali',                'bali',                 'Bali',        'Indonesia', 'international', 'Island of the Gods', 'Paradise Found', 'published', TRUE, 11),
    ('Bangkok',             'bangkok',              'Bangkok',     'Thailand', 'international', 'City of Angels', 'Vibrant & Exotic', 'published', TRUE, 12),
    ('Maldives',            'maldives',             'Malé',        'Maldives', 'international', 'Tropical paradise in the Indian Ocean', 'Overwater Bliss', 'published', TRUE, 13),
    ('Singapore',           'singapore',            'Singapore',   'Singapore', 'international', 'The Lion City', 'Where East Meets West', 'published', TRUE, 14),
    ('Tokyo',               'tokyo',                'Tokyo',       'Japan', 'international', 'The Electric Metropolis', 'Tradition Meets Future', 'published', TRUE, 15);

-- ============================================================
-- SEED DATA: Packages
-- ============================================================
INSERT INTO packages (title, slug, description, short_description, destination, state, country, duration_days, duration_nights, starting_price, category, tags, status, featured) VALUES
    ('Kerala Backwater & Beaches', 'kerala-backwater-beaches',
     'Experience the serene backwaters of Alleppey and the pristine beaches of Kovalam in this 5-day Kerala tour package.',
     'Explore Kerala backwaters, beaches, and lush green landscapes',
     'Alleppey', 'Kerala', 'India', 5, 4, 12499, 'domestic',
     'honeymoon,romantic,beach,backwater,kerala,couple',
     'published', TRUE),

    ('Goa Beach Holiday', 'goa-beach-holiday',
     'Enjoy the vibrant beaches, nightlife, and Portuguese heritage of Goa in this 4-day getaway.',
     'Sun, sand, and nightlife in Goa',
     'Goa', 'Goa', 'India', 4, 3, 8999, 'domestic',
     'beach,honeymoon,couple,nightlife,goa,budget',
     'published', TRUE),

    ('Rajasthan Royal Tour', 'rajasthan-royal-tour',
     'Explore the majestic forts, palaces, and desert landscapes of Rajasthan in this 7-day royal tour.',
     'Discover the royalty of Rajasthan',
     'Jaipur', 'Rajasthan', 'India', 7, 6, 14999, 'domestic',
     'heritage,fort,palace,desert,rajasthan,royal,culture',
     'published', TRUE),

    ('Swiss Alps Adventure', 'swiss-alps-adventure',
     'Experience the breathtaking Swiss Alps with stunning mountain views, pristine lakes, and charming villages.',
     'Majestic Swiss Alps adventure',
     'Swiss Alps', NULL, 'Switzerland', 8, 7, 185000, 'international',
     'adventure,mountain,luxury,honeymoon,switzerland,europe',
     'published', TRUE),

    ('Thailand Explorer', 'thailand-explorer',
     'Discover the beauty of Thailand from Bangkok temples to Phuket beaches.',
     'Explore temples, beaches, and nightlife in Thailand',
     'Bangkok', NULL, 'Thailand', 6, 5, 45000, 'international',
     'beach,adventure,temple,thailand,bangkok,phuket,couple',
     'published', TRUE),

    ('Dubai Luxury Experience', 'dubai-luxury-experience',
     'Experience the luxury and adventure of Dubai with desert safari, Burj Khalifa, and world-class shopping.',
     'Luxury living in the City of Gold',
     'Dubai', NULL, 'UAE', 5, 4, 75000, 'international',
     'luxury,dubai,desert,shopping,family,adventure',
     'published', TRUE),

    ('Andaman Island Escape', 'andaman-island-escape',
     'Discover the pristine beaches, crystal-clear waters, and vibrant coral reefs of Andaman Islands.',
     'Tropical island paradise adventure',
     'Havelock Island', 'Andaman', 'India', 5, 4, 18999, 'domestic',
     'beach,island,scuba,snorkeling,andaman,adventure,honeymoon',
     'published', TRUE),

    ('North East Explorer', 'north-east-explorer',
     'Explore the unexplored beauty of North East India with tea gardens, living root bridges, and wildlife.',
     'Discover India hidden gem',
     'Gangtok', 'Sikkim', 'India', 7, 6, 22000, 'domestic',
     'mountain,adventure,northeast,culture,wildlife,trekking',
     'published', TRUE);

-- ============================================================
-- SEED DATA: Site Settings
-- ============================================================
INSERT INTO site_settings (key, value) VALUES
    ('site_name',       'TravelVista'),
    ('site_tagline',    'Explore the Incredible India'),
    ('site_email',      'info@travelvista.com'),
    ('site_phone',      '+91 98765 43210'),
    ('site_whatsapp',   '+91 98765 43210'),
    ('site_address',    'Mumbai, Maharashtra, India'),
    ('facebook_url',    'https://facebook.com/travelvista'),
    ('instagram_url',   'https://instagram.com/travelvista'),
    ('youtube_url',     'https://youtube.com/travelvista'),
    ('twitter_url',     'https://twitter.com/travelvista'),
    ('hero_title',      'Explore the Incredible India'),
    ('hero_subtitle',   'Discover breathtaking destinations, curated packages, and unforgettable experiences'),
    ('about_title',     'About TravelVista'),
    ('about_content',   'Your trusted travel partner since 2020'),
    ('footer_text',     '© 2026 TravelVista. All rights reserved.'),
    ('meta_title',      'TravelVista - Explore India & Beyond'),
    ('meta_description','Book curated travel packages, hotels, and experiences across India and internationally'),
    ('analytics_id',    '');

-- ============================================================
-- SEED DATA: Testimonials
-- ============================================================
INSERT INTO testimonials (name, location, quote, rating, status) VALUES
    ('Priya Sharma',  'Mumbai',     'Amazing experience! The Kerala backwater trip was perfectly organized. Will book again!', 5, 'published'),
    ('Rahul Patel',   'Ahmedabad',  'Rajasthan tour was a dream come true. Every detail was taken care of.', 5, 'published'),
    ('Anita Desai',   'Delhi',      'Goa beach holiday was fantastic. Great value for money and wonderful service.', 4, 'published'),
    ('Vikram Singh',  'Bangalore',  'The Swiss Alps package was beyond our expectations. Truly premium experience.', 5, 'published'),
    ('Sneha Reddy',   'Hyderabad',  'Andaman trip was magical! Crystal clear waters and pristine beaches.', 5, 'published');

-- ============================================================
-- END OF SCHEMA
-- Total: 23 tables
--   1.  roles
--   2.  users
--   3.  countries
--   4.  states
--   5.  cities
--   6.  destinations
--   7.  packages
--   8.  hotels
--   9.  activities
--   10. blogs
--   11. bookings
--   12. enquiries
--   13. leads
--   14. reviews
--   15. wishlists
--   16. testimonials
--   17. faqs
--   18. site_settings
--   19. menus
--   20. menu_destinations (join table)
--   21. otp_verifications (code_hash + auth purposes added)
--   22. edit_sessions
--   23. admin_audit_logs
-- ============================================================


-- Check user ID 11 without exposing password_hash.
SELECT
    u.id,
    u.name,
    u.email,
    u.phone,
    r.name AS role,
    u.is_active,
    u.email_verified,
    u.phone_verified,
    u.created_at,
    u.updated_at
FROM users u
LEFT JOIN roles r ON r.id = u.role_id
WHERE u.id = 11;

-- Existing admin verification query (also excludes password_hash).
SELECT
    u.id,
    u.name,
    u.email,
    u.phone,
    r.name AS role,
    u.is_active,
    u.email_verified,
    u.phone_verified,
    u.created_at,
    u.updated_at
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE r.name = 'super_admin';