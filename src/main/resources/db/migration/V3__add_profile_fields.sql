-- Optional profile details. Everything here is the user's choice to fill in.
-- These fields power future features: born-on reminders, places and people,
-- lifestyle context, and personalised rediscovery of memories.
ALTER TABLE users
    ADD COLUMN date_of_birth       DATE,
    ADD COLUMN phone               VARCHAR(30),
    ADD COLUMN gender              VARCHAR(20),
    ADD COLUMN city                VARCHAR(100),
    ADD COLUMN country             VARCHAR(100),
    ADD COLUMN profession          VARCHAR(100),
    ADD COLUMN relationship_status VARCHAR(20),
    ADD COLUMN languages           VARCHAR(200);