-- A user whose password was set by the owner (create or reset) must choose their own before using the API.
ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
