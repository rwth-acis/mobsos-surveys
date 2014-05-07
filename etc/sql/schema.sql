drop schema if exists mobsos;
create schema if not exists mobsos default character set utf8 collate utf8_general_ci;
use mobsos;

grant usage on *.* to acdsense@localhost identified by 'dito'; 
grant all privileges on mobsos.* to acdsense@localhost;

drop table if exists survey;
drop table if exists questionnaire;
drop table if exists survey_structure;

-- -----------------------------------------------------
-- Definition table 'survey'
-- -----------------------------------------------------
create table survey (
	id mediumint not null auto_increment,
	owner varchar(128) not null,
	name varchar(128) not null,
	description varchar(512) not null,
	resource varchar(512) not null,
	start datetime(6) not null,
	end datetime(6) not null,
	constraint surveypk primary key (id),
	constraint survey_uk unique key (name),
	constraint survey_time check (end_time > start_time)
);

create index idx_s_owner on survey(owner);
create fulltext index idx_s_topic on survey(resource);
create fulltext index idx_s_desc on survey(description);

-- -----------------------------------------------------
-- Definition table 'questionnaire'
-- -----------------------------------------------------
create table questionnaire (
	id mediumint not null auto_increment,
	name varchar(128) not null,
	description varchar(512) not null,
	form mediumtext not null,
	constraint questionnaire_pk primary key (id),
	constraint questionnaire_uk unique key (name)
);

create fulltext index idx_q_desc on questionnaire (description);
create fulltext index idx_q_form on questionnaire (form);

-- -----------------------------------------------------
-- Definition table 'survey_structure'
-- -----------------------------------------------------
create table survey_structure ( 
	sid mediumint not null,
	qid mediumint not null,
	cid mediumint not null,
	constraint struct_pk primary key (sid,qid,cid),
	constraint struct_fk_sid foreign key (sid) references survey(id)
		on delete cascade
		on update no action,
	constraint struct_fk_qid foreign key (qid) references questionnaire(id)
		on delete cascade
		on update no action 
);

