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
    start datetime not null,
    end datetime not null,
    qid mediumint,
    constraint surveypk primary key (id),
    constraint survey_uk unique key (name),
    constraint survey_q_fk foreign key (qid)
        references questionnaire (id)
        on delete cascade on update no action,
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
    time datetime not null,
    constraint res_pk primary key (id),
    constraint res_uk unique key (uid , sid , qkey),
    constraint res_fk foreign key (sid)
        references survey (id)
        on delete cascade on update no action
);

create table feedback (
    user_id varchar(128) not null,
    client_id varchar(128) not null,
    rating smallint not null,
    comment varchar(2048),
    time datetime not null default current_timestamp,
    constraint rating_pk primary key (user_id , client_id)
);

insert into feedback(client_id,user_id, rating, comment) values 
('5952396a-64c9-427c-97f0-bec19c7c951b', '91067.glgismds',1,"Besides nice UI it's crap!"),
('5952396a-64c9-427c-97f0-bec19c7c951b', '10678', 1,"Well, could be better."),
('5952396a-64c9-427c-97f0-bec19c7c951b', '60535', 4,"It works. No more, no less."),
('5952396a-64c9-427c-97f0-bec19c7c951b', '90221.ikwpckvm', 1,"Nothing for me..."), 
('5952396a-64c9-427c-97f0-bec19c7c951b', '24459', 2,"Cool, but unbearably unstable..."), 
('5952396a-64c9-427c-97f0-bec19c7c951b', '77385', 4,"Aaaaw... almost perfect."),
('5952396a-64c9-427c-97f0-bec19c7c951b', '73021.kfzhnnaa',2,"Hammer!"),
('5952396a-64c9-427c-97f0-bec19c7c951b', '34007', 5,"Yeah!"),
('5952396a-64c9-427c-97f0-bec19c7c951b', '65774.daiboefi', 3,"Works on my tablet! Love it!"),
('5952396a-64c9-427c-97f0-bec19c7c951b', '53288.rxkfsrzz', 4,"Damn it. Nice, but crashes unpredictably."),
('5952396a-64c9-427c-97f0-bec19c7c951b', '33471.qewxzfyi', 5,"Perfect"),
('5952396a-64c9-427c-97f0-bec19c7c951b', '81705', 3,"Nicer would be nice");