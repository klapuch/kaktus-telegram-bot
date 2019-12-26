CREATE TABLE results (
	id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	page text NOT NULL,
	section text NOT NULL,
	selector text NOT NULL,
	stored_at timestamptz NOT NULL DEFAULT now()
);
