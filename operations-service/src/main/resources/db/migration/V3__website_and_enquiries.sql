create table website_settings (
 id integer primary key check(id=1), recipient_email varchar(254) not null,
 hero_video_url varchar(1000) not null default ''
);
insert into website_settings(id,recipient_email) values(1,'thearksalonandspa@gmail.com');
create table enquiry (
 id uuid primary key, kind varchar(16) not null check(kind in ('CAREER','CONTACT','BOOKING','COMPLAINT')),
 name varchar(100) not null, email varchar(254) not null, phone varchar(16) not null,
 subject varchar(200) not null, message varchar(5000) not null, answers jsonb not null,
 resume bytea, resume_name varchar(120), status varchar(20) not null default 'OPEN' check(status in ('OPEN','IN_REVIEW','RESOLVED')),
 created_at timestamptz not null default now()
);
create index enquiry_date_idx on enquiry(created_at desc);
create table enquiry_mail (
 enquiry_id uuid primary key references enquiry(id), recipient_email varchar(254) not null,
 attempts integer not null default 0, next_attempt timestamptz not null default now(), sent_at timestamptz,
 state varchar(16) not null default 'QUEUED' check(state in ('QUEUED','SENT','FAILED'))
);
create table blog_post (
 id uuid primary key, title varchar(180) not null, excerpt varchar(400) not null,
 body varchar(20000) not null, published boolean not null default false,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
insert into blog_post(id,title,excerpt,body,published) values
('b1000000-0000-0000-0000-000000000001','Your first visit to The Ark','A little preparation makes room for a calmer salon and spa experience.','Tell our team what you would like from your visit and ask about available services before booking. Share any allergies or relevant sensitivities with your therapist.\n\nArrive with time to settle in, ask questions and agree on your treatment. For booking enquiries, use the contact form; our team will confirm availability with you.',true),
('b1000000-0000-0000-0000-000000000002','Make time for a little care','Simple ways to plan a salon or spa visit around your day.','Choose a time when you can enjoy your visit without rushing. Ask our team how long your selected service usually takes and let us know your preferences.\n\nComfort is personal: you can ask to adjust the room temperature, pressure or music. Speak to your therapist whenever you need a change.',true);
