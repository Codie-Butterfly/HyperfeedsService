CREATE TABLE feed_calculator_profiles (
    code VARCHAR(50) PRIMARY KEY,
    animal_name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    default_days INTEGER NOT NULL CHECK (default_days > 0),
    display_order INTEGER NOT NULL,
    source_label VARCHAR(255) NOT NULL,
    source_url TEXT,
    disclaimer TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE feed_calculator_phases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_code VARCHAR(50) NOT NULL REFERENCES feed_calculator_profiles(code) ON DELETE CASCADE,
    phase_name VARCHAR(120) NOT NULL,
    product_sku VARCHAR(80) REFERENCES products(sku),
    rate_mode VARCHAR(20) NOT NULL CHECK (rate_mode IN ('FIXED', 'DAILY')),
    rate_kg_per_animal NUMERIC(12, 4) NOT NULL CHECK (rate_kg_per_animal >= 0),
    phase_days INTEGER NOT NULL DEFAULT 1 CHECK (phase_days > 0),
    uses_requested_days BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    notes TEXT
);

CREATE INDEX idx_feed_calculator_phases_profile
    ON feed_calculator_phases(profile_code, display_order);

INSERT INTO feed_calculator_profiles
    (code, animal_name, description, default_days, display_order, source_label, source_url, disclaimer)
VALUES
('BROILER','Broilers','Full-cycle feed estimate for broilers.',35,1,'Hyperfeeds website demo calculator','https://hyperfeeds.co.zw/feed_calculator.html','Demo estimate only. Actual intake varies by breed, age, health, temperature, management and feed wastage.'),
('LAYER','Layers','Rearing phases plus a selectable laying period.',30,2,'Hyperfeeds website and Hy-Line Brown intake range','https://www.hyline.com/filesimages/Hy-Line-Products/Hy-Line-Product-PDFs/Brown/Brown%20Alt/BRN%20ALT%20COM%20AUS.pdf','Demo estimate only. Select days for the laying period; rearing phase quantities are included once.'),
('ROAD_RUNNER','Road Runners','Feed estimate for indigenous and free-range chickens.',30,3,'Demo planning assumption',NULL,'Demo estimate only. Adjust for age, scavenging access, growth target and local conditions.'),
('PIG','Pigs','Illustrative weaner-to-finisher feed programme.',120,4,'Demo planning assumption informed by PIC phase feeding guidance','https://www.pic.com/resources/wean-to-finish-manual-english/','Demo estimate only. Pig intake depends strongly on starting weight, genetics, sex, housing and target market weight.'),
('DAIRY_CATTLE','Dairy Cattle','Dairy meal supplement estimate for a selected period.',30,5,'Demo planning assumption informed by Teagasc dairy nutrition guidance','https://teagasc.ie/animals/dairy/nutrition/','Demo supplement estimate only. A complete ration must consider body weight, milk yield, forage quality, lactation stage and veterinary or nutritionist advice.'),
('BEEF_CATTLE','Beef Cattle','Beef finishing meal estimate for a selected period.',30,6,'Demo planning assumption',NULL,'Demo supplement estimate only. Introduce concentrates gradually and balance them with suitable forage and professional advice.'),
('CALF','Calves','Illustrative starter and grower feed programme.',90,7,'Demo planning assumption',NULL,'Demo estimate only. Milk or milk replacer, forage, water, age and weaning management are not included.'),
('GOAT_SHEEP','Goats & Sheep','Supplement estimate for goats and sheep.',30,8,'Demo planning assumption',NULL,'Demo supplement estimate only. Requirements differ by species, weight, pregnancy, lactation and forage quality.'),
('RABBIT','Rabbits','Rabbit pellet estimate for a selected period.',30,9,'Demo planning assumption',NULL,'Demo estimate only. Requirements vary with size, growth, pregnancy, lactation and forage provided.'),
('DOG','Dogs','Dog food estimate for a selected period.',30,10,'Demo planning assumption',NULL,'Demo estimate only. Feed according to product labelling, body condition, age and veterinary advice.'),
('GAME','Game Animals','Game block planning estimate for a selected period.',30,11,'Demo planning assumption',NULL,'Demo supplement estimate only. A mineral block is not a complete diet and consumption varies widely by species and grazing conditions.');

INSERT INTO feed_calculator_phases
    (profile_code, phase_name, product_sku, rate_mode, rate_kg_per_animal, phase_days, uses_requested_days, display_order, notes)
VALUES
('BROILER','Starter','HF-WEB-BROILER-STARTER-CRUMBS','FIXED',0.5000,1,FALSE,1,'Website demo allocation per bird.'),
('BROILER','Grower crumbs','HF-WEB-BROILER-GROWER-CRUMBS','FIXED',0.5000,1,FALSE,2,'Website demo allocation per bird.'),
('BROILER','Grower pellets','HF-WEB-BROILER-GROWER-PELLETS','FIXED',1.0000,1,FALSE,3,'Website demo allocation per bird.'),
('BROILER','Finisher','HF-WEB-BROILER-FINISHER','FIXED',1.5000,1,FALSE,4,'Website demo allocation per bird.'),
('LAYER','Chick starter','HF-WEB-BROILER-STARTER-CRUMBS','FIXED',1.8500,1,FALSE,1,'Website demo rearing allocation per bird.'),
('LAYER','Developer','HF-WEB-LAYER-MASH','FIXED',4.4000,1,FALSE,2,'Website demo rearing allocation per bird; mapped to current demo catalogue.'),
('LAYER','Pre-lay','HF-WEB-LAYER-MASH','FIXED',1.0000,1,FALSE,3,'Website demo rearing allocation per bird; mapped to current demo catalogue.'),
('LAYER','In lay','HF-WEB-LAYER-MASH','DAILY',0.1100,30,TRUE,4,'110 g per bird per selected day.'),
('ROAD_RUNNER','Maintenance and growth','HF-WEB-ROAD-RUNNER','DAILY',0.1000,30,TRUE,1,'Demo daily allocation.'),
('PIG','Weaner/creep','HF-WEB-PIG-WEANER','FIXED',25.0000,1,FALSE,1,'Demo phase allocation per pig.'),
('PIG','Grower','HF-WEB-PIG-GROWER','FIXED',100.0000,1,FALSE,2,'Demo phase allocation per pig.'),
('PIG','Finisher','HF-WEB-PIG-FINISHER','FIXED',125.0000,1,FALSE,3,'Demo phase allocation per pig.'),
('DAIRY_CATTLE','Dairy meal supplement','HF-WEB-DAIRY-18','DAILY',4.0000,30,TRUE,1,'Demo daily supplement per cow.'),
('BEEF_CATTLE','Fattening meal','HF-WEB-BEEF-FATTENING','DAILY',6.0000,30,TRUE,1,'Demo daily finishing allocation per animal.'),
('CALF','Calf starter','HF-WEB-CALF-STARTER','FIXED',45.0000,1,FALSE,1,'Demo phase allocation per calf.'),
('CALF','Calf grower','HF-WEB-CALF-GROWER','FIXED',90.0000,1,FALSE,2,'Demo phase allocation per calf.'),
('GOAT_SHEEP','Goat and sheep supplement','HF-WEB-GOAT-SHEEP','DAILY',0.5000,30,TRUE,1,'Demo daily supplement per animal.'),
('RABBIT','Rabbit pellets','HF-WEB-RABBIT-PELLETS','DAILY',0.1500,30,TRUE,1,'Demo daily allocation per rabbit.'),
('DOG','Dog food','HF-WEB-DOG-FOOD','DAILY',0.4000,30,TRUE,1,'Demo daily allocation per dog.'),
('GAME','Game block supplement','HF-WEB-GAME-BLOCK','DAILY',0.1000,30,TRUE,1,'Demo average block consumption per animal.');
