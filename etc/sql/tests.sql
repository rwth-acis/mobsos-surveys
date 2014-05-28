
insert into mobsos.questionnaire(owner, organization, logo, name, description) 
values (123456789,"RWTH","http://bla.org","Do you like Questionnaire","A sample questionnaire");

select * from mobsos.questionnaire;

insert into mobsos.survey(owner, organization, logo, name, description, resource, start, end, qid ) 
values (123456789,"RWTH","http://blub.org","Antonio Banderas Survey",
"A simple survey on Antonio","http://antonio.org","2014-05-30 00:00:00","2014-06-30 23:59:59", 1);

insert into mobsos.survey_result(uid,cid,sid,qkey,qval,time) values

(1,1,1,"A.2.1",2,"2014-06-01 00:03:00"),
(1,1,1,"A.2.2",1,"2014-06-01 00:03:02"),
(1,1,1,"A.2.3","Ich fänd Kühe öcht sohper!","2014-06-01 00:03:03"),

(2,1,1,"A.2.1",4,"2014-06-24 00:12:40"),
(2,1,1,"A.2.2",1,"2014-06-24 00:12:59"),
(2,1,1,"A.2.3","Ein Freitäxt mit S%§$erzeichen","2014-06-24 00:13:03"),

(3,1,1,"A.2.1",4,"2014-06-03 12:45:00"),
(3,1,1,"A.2.2",0,"2014-06-03 12:46:22"),
(3,1,1,"A.2.3","Och näööh...","2014-06-03 13:03:03"),

(4,1,1,"A.2.1",1,"2014-06-14 23:12:10"),
(4,1,1,"A.2.2",1,"2014-06-14 23:13:22"),
(4,1,1,"A.2.3","Supper!","2014-06-14 23:13:44"),

(5,1,1,"A.2.1",5,"2014-06-01 00:03:00"),
(5,1,1,"A.2.2",0,"2014-06-01 00:03:02"),
(5,1,1,"A.2.3","Ich fänd Pförde goil!","2014-06-01 00:03:03"),

(2,2,1,"A.2.1",4,"2014-06-11 01:12:40"),
(2,2,1,"A.2.2",1,"2014-06-11 01:12:59"),
(2,2,1,"A.2.3","Ein hoch auf die 2. Community!","2014-06-11 01:13:03"),

(6,2,1,"A.2.1",4,"2014-06-13 13:45:00"),
(6,2,1,"A.2.2",0,"2014-06-13 14:46:22"),
(6,2,1,"A.2.3","Freiheit für Kommune 2!","2014-06-13 15:03:03"),

(3,2,1,"A.2.1",1,"2014-06-15 14:12:10"),
(3,2,1,"A.2.2",1,"2014-06-15 14:13:55"),
(3,2,1,"A.2.3","Ich bin stolz, ein 2er zu sein!","2014-06-15 14:55:22")
;

select distinct(qkey) from mobsos.survey_result;

select uid, cid,
MAX(IF(qkey = 'A.2.1', cast(qval as unsigned), NULL)) AS "A.2.1",
MAX(IF(qkey = 'A.2.2', cast(qval as unsigned), NULL)) AS "A.2.2",
MAX(IF(qkey = 'A.2.3', qval, NULL)) AS "A.2.3"
from mobsos.survey_result where sid = 1 group by uid, cid;

select * from mobsos.survey_result;

delete from mobsos.survey_result;
delete from mobsos.survey;
delete from mobsos.questionnaire;

alter table mobsos.questionnaire auto_increment = 1;
alter table mobsos.survey auto_increment = 1;

select * from questionnaire;
select * from survey;

