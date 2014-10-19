drop schema if exists mobsos;
create schema if not exists mobsos default character set utf8 collate utf8_general_ci;
use mobsos;

grant usage on *.* to mobsos@localhost identified by 'mobsosrules'; 
grant all privileges on mobsos.* to mobsos@localhost;

-- -----------------------------------------------------
-- Definition table 'questionnaire'
-- -----------------------------------------------------
create table questionnaire (
	id mediumint not null auto_increment,
	owner varchar(128) not null,
	organization varchar(128) not null,
	logo varchar(200) not null,
	name varchar(128) not null,
	description varchar(2048) not null,
	lang varchar(32) not null,
	form mediumtext,
	constraint questionnaire_pk primary key (id),
	constraint questionnaire_uk unique key (name)
);

create index idx_q_own on questionnaire (owner);
create index idx_q_org on questionnaire (organization);
create index idx_q_log on questionnaire (logo);

-- -----------------------------------------------------
-- Definition table 'survey'
-- -----------------------------------------------------
create table survey (
	id mediumint not null auto_increment,
	owner varchar(128) not null,
	organization varchar(128) not null,
	logo varchar(200) not null,
	name varchar(128) not null,
	description varchar(2048) not null,
	resource varchar(200) not null,
	lang varchar(32) not null,
	start datetime(6) not null,
	end datetime(6) not null,
	qid mediumint,
	constraint surveypk primary key (id),
	constraint survey_uk unique key (name),
	constraint survey_q_fk foreign key (qid) references questionnaire (id)
		on delete cascade
		on update no action
	,
	constraint survey_time check (end_time > start_time)
);

create index idx_s_owner on survey(owner);
create index idx_s_org on survey (organization);
create index idx_s_log on survey (logo);
create index idx_s_topic on survey(resource);

-- -----------------------------------------------------
-- Definition table 'response'
-- -----------------------------------------------------
create table response (
	id bigint not null auto_increment,
	uid varchar(128) not null,
	sid mediumint not null,
	qkey varchar(32) not null,
	qval varchar(512) not null,
	time datetime(6),
	constraint res_pk primary key(id),
	constraint res_uk unique key(uid,sid,qkey),
	constraint res_fk foreign key (sid) references survey(id)
		on delete cascade
		on update no action
);

create table resource (
	uri varchar(200) not null,
	name varchar(128) not null,
	description varchar(2048),
	constraint reso_pk primary key(uri)
);

insert into resource (uri,name,description) values ("http://achso.aalto.fi","AchSo!","A mobile application recording, sharing, and annotating videos");
insert into resource (uri,name,description) values ("https://github.com/learning-layers/sevianno","SeViAnno!","A Web application for the semantic annotation of videos.");
insert into resource (uri,name,description) values ("http://odl.learning-layers.eu/learning-toolbox","Learning Toolbox", "A mobile work-based learning aid.");
insert into resource (uri,name,description) values ("http://wikipedia.org","Wikipedia", "A crowdsourced online encyclopedia.");



