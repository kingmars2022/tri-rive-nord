-- --------------------------------------------------------------------------
-- Four municipalities instead of one.
--
-- Until now the application was Blainville and nothing else: `sector` was the
-- enum north/south/all, which is Blainville's boulevard de la Seigneurie and
-- means nothing in Sainte-Therese. This migration makes the municipality a
-- row rather than an assumption, and makes sectors data belonging to one.
--
-- What this migration deliberately does NOT do is invent the other three
-- cities' schedules or sorting rules. Each one is seeded with its name and
-- nothing else, and `calendar_verified` / `guide_verified` stay false until
-- its own published document has been read and checked against. Guessing
-- here would repeat the defect V15 fixed, four times over: a calendar that
-- every test passes while it prints the wrong bin. A city with no data must
-- read as "not loaded yet" in the application, never as a quiet week.
-- --------------------------------------------------------------------------

create table municipality (
    id bigint primary key auto_increment,
    slug varchar(40) not null,
    name varchar(120) not null,
    province varchar(10) not null default 'QC',
    source_url varchar(1000),
    -- Separate flags because the two data sets arrive from different
    -- documents and one can be loaded without the other.
    calendar_verified boolean not null default false,
    guide_verified boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uq_municipality_slug (slug)
);

-- Sectors are per municipality because they are a local fact: Blainville
-- splits at a boulevard, and whether any other city splits at all is not
-- known until its calendar is read. 'all' is kept as a row rather than a
-- special case in code, so a rule covering the whole territory joins the
-- same way as one covering half of it.
create table municipality_sector (
    id bigint primary key auto_increment,
    municipality_id bigint not null,
    code varchar(40) not null,
    position int not null default 0,
    name_fr varchar(255) not null,
    name_en varchar(255) not null,
    name_zh varchar(255) not null,
    source_url varchar(1000),
    foreign key (municipality_id) references municipality(id),
    unique key uq_municipality_sector (municipality_id, code)
);

insert into municipality (slug, name, source_url, calendar_verified, guide_verified) values
('blainville', 'Blainville',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles',
 true, true),
('sainte-therese', 'Sainte-Therese', null, false, false),
('boisbriand',     'Boisbriand',     null, false, false),
('rosemere',       'Rosemere',       null, false, false);

set @blainville = (select id from municipality where slug = 'blainville');

insert into municipality_sector
(municipality_id, code, position, name_fr, name_en, name_zh, source_url) values
(@blainville, 'all',   0,
 'Tout le territoire', 'Whole city', '全市',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
(@blainville, 'south', 1,
 'Au sud du boulevard de la Seigneurie', 'South of boulevard de la Seigneurie',
 'boulevard de la Seigneurie 以南',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles'),
(@blainville, 'north', 2,
 'Au nord du boulevard de la Seigneurie', 'North of boulevard de la Seigneurie',
 'boulevard de la Seigneurie 以北',
 'https://blainville.ca/services/environnement-et-voirie/collectes-et-matieres-residuelles');
